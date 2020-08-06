/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "car-bugreportd"

#include <android-base/errors.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <cutils/sockets.h>
#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <gui/SurfaceComposerClient.h>
#include <log/log_main.h>
#include <private/android_filesystem_config.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#include <ziparchive/zip_writer.h>

#include <chrono>
#include <string>
#include <vector>

namespace {
// Directory used for keeping temporary files
constexpr const char* kTempDirectory = "/data/user_de/0/com.android.shell/temp_bugreport_files";
// Socket to write the progress information.
constexpr const char* kCarBrProgressSocket = "car_br_progress_socket";
// Socket to write the zipped bugreport file.
constexpr const char* kCarBrOutputSocket = "car_br_output_socket";
// Socket to write the extra bugreport zip file. This zip file contains data that does not exist
// in bugreport file generated by dumpstate.
constexpr const char* kCarBrExtraOutputSocket = "car_br_extra_output_socket";
// The prefix used by bugreportz protocol to indicate bugreport finished successfully.
constexpr const char* kOkPrefix = "OK:";
// Number of connect attempts to dumpstate socket
constexpr const int kMaxDumpstateConnectAttempts = 20;
// Wait time between connect attempts
constexpr const int kWaitTimeBetweenConnectAttemptsInSec = 1;
// Wait time for dumpstate. Set a timeout so that if nothing is read in 10 minutes, we'll stop
// reading and quit. No timeout in dumpstate is longer than 60 seconds, so this gives lots of leeway
// in case of unforeseen time outs.
constexpr const int kDumpstateTimeoutInSec = 600;
// The prefix for screenshot filename in the generated zip file.
constexpr const char* kScreenshotPrefix = "/screenshot";

using android::OK;
using android::PhysicalDisplayId;
using android::status_t;
using android::SurfaceComposerClient;

// Returns a valid socket descriptor or -1 on failure.
int openSocket(const char* service) {
    int s = android_get_control_socket(service);
    if (s < 0) {
        ALOGE("android_get_control_socket(%s): %s", service, strerror(errno));
        return -1;
    }
    fcntl(s, F_SETFD, FD_CLOEXEC);
    if (listen(s, 4) < 0) {
        ALOGE("listen(control socket): %s", strerror(errno));
        return -1;
    }

    struct sockaddr addr;
    socklen_t alen = sizeof(addr);
    int fd = accept(s, &addr, &alen);
    if (fd < 0) {
        ALOGE("accept(control socket): %s", strerror(errno));
        return -1;
    }
    return fd;
}

// Processes the given dumpstate progress protocol |line| and updates
// |out_last_nonempty_line| when |line| is non-empty, and |out_zip_path| when
// the bugreport is finished.
void processLine(const std::string& line, std::string* out_zip_path,
                 std::string* out_last_nonempty_line) {
    // The protocol is documented in frameworks/native/cmds/bugreportz/readme.md
    if (line.empty()) {
        return;
    }
    *out_last_nonempty_line = line;
    if (line.find(kOkPrefix) != 0) {
        return;
    }
    *out_zip_path = line.substr(strlen(kOkPrefix));
    return;
}

// Sends the contents of the zip fileto |outfd|.
// Returns true if success
void zipFilesToFd(const std::vector<std::string>& extra_files, int outfd) {
    // pass fclose as Deleter to close the file when unique_ptr is destroyed.
    std::unique_ptr<FILE, decltype(fclose)*> outfile = {fdopen(outfd, "wb"), fclose};
    if (outfile == nullptr) {
        ALOGE("Failed to open output descriptor");
        return;
    }
    auto writer = std::make_unique<ZipWriter>(outfile.get());

    int error = 0;
    for (const auto& filepath : extra_files) {
        const auto name = android::base::Basename(filepath);

        error = writer->StartEntry(name.c_str(), 0);
        if (error) {
            ALOGE("Failed to start entry %s", writer->ErrorCodeString(error));
            return;
        }
        android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(filepath.c_str(), O_RDONLY)));
        if (fd == -1) {
            return;
        }
        while (1) {
            char buffer[65536];

            ssize_t bytes_read = TEMP_FAILURE_RETRY(read(fd, buffer, sizeof(buffer)));
            if (bytes_read == 0) {
                break;
            }
            if (bytes_read == -1) {
                if (errno == EAGAIN) {
                    ALOGE("timed out while reading %s", name.c_str());
                } else {
                    ALOGE("read terminated abnormally (%s)", strerror(errno));
                }
                // fail immediately
                return;
            }
            error = writer->WriteBytes(buffer, bytes_read);
            if (error) {
                ALOGE("WriteBytes() failed %s", ZipWriter::ErrorCodeString(error));
                // fail immediately
                return;
            }
        }

        error = writer->FinishEntry();
        if (error) {
            ALOGE("failed to finish entry %s", writer->ErrorCodeString(error));
            continue;
        }
    }
    error = writer->Finish();
    if (error) {
        ALOGE("failed to finish zip writer %s", writer->ErrorCodeString(error));
    }
}

int copyTo(int fd_in, int fd_out, void* buffer, size_t buffer_len) {
    ssize_t bytes_read = TEMP_FAILURE_RETRY(read(fd_in, buffer, buffer_len));
    if (bytes_read == 0) {
        return 0;
    }
    if (bytes_read == -1) {
        // EAGAIN really means time out, so make that clear.
        if (errno == EAGAIN) {
            ALOGE("read timed out");
        } else {
            ALOGE("read terminated abnormally (%s)", strerror(errno));
        }
        return -1;
    }
    // copy all bytes to the output socket
    if (!android::base::WriteFully(fd_out, buffer, bytes_read)) {
        ALOGE("write failed");
        return -1;
    }
    return bytes_read;
}

bool copyFile(const std::string& zip_path, int output_socket) {
    android::base::unique_fd fd(TEMP_FAILURE_RETRY(open(zip_path.c_str(), O_RDONLY)));
    if (fd == -1) {
        ALOGE("Failed to open zip file %s.", zip_path.c_str());
        return false;
    }
    while (1) {
        char buffer[65536];
        int bytes_copied = copyTo(fd, output_socket, buffer, sizeof(buffer));
        if (bytes_copied == 0) {
            break;
        }
        if (bytes_copied == -1) {
            ALOGE("Failed to copy zip file %s to the output_socket.", zip_path.c_str());
            return false;
        }
    }
    return true;
}

// Triggers a bugreport and waits until it is all collected.
// returns false if error, true if success
bool doBugreport(int progress_socket, size_t* out_bytes_written, std::string* zip_path) {
    // Socket will not be available until service starts.
    android::base::unique_fd s;
    for (int i = 0; i < kMaxDumpstateConnectAttempts; i++) {
        s.reset(socket_local_client("dumpstate", ANDROID_SOCKET_NAMESPACE_RESERVED, SOCK_STREAM));
        if (s != -1) break;
        sleep(kWaitTimeBetweenConnectAttemptsInSec);
    }

    if (s == -1) {
        ALOGE("failed to connect to dumpstatez service");
        return false;
    }

    // Set a timeout so that if nothing is read by the timeout, stop reading and quit
    struct timeval tv = {
        .tv_sec = kDumpstateTimeoutInSec,
        .tv_usec = 0,
    };
    if (setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) != 0) {
        ALOGW("Cannot set socket timeout (%s)", strerror(errno));
    }

    std::string line;
    std::string last_nonempty_line;
    char buffer[65536];
    while (true) {
        ssize_t bytes_read = copyTo(s, progress_socket, buffer, sizeof(buffer));
        if (bytes_read == 0) {
            break;
        }
        if (bytes_read == -1) {
            ALOGE("Failed to copy progress to the progress_socket.");
            return false;
        }
        // Process the buffer line by line. this is needed for the filename.
        for (int i = 0; i < bytes_read; i++) {
            char c = buffer[i];
            if (c == '\n') {
                processLine(line, zip_path, &last_nonempty_line);
                line.clear();
            } else {
                line.append(1, c);
            }
        }
        *out_bytes_written += bytes_read;
    }
    s.reset();
    // Process final line, in case it didn't finish with newline.
    processLine(line, zip_path, &last_nonempty_line);
    // if doBugReport finished successfully, zip path should be set.
    if (zip_path->empty()) {
        ALOGE("no zip file path was found in bugreportz progress data");
        return false;
    }
    return true;
}

bool waitpid_with_timeout(pid_t pid, int timeout_secs, int* status) {
    sigset_t child_mask, old_mask;
    sigemptyset(&child_mask);
    sigaddset(&child_mask, SIGCHLD);

    if (sigprocmask(SIG_BLOCK, &child_mask, &old_mask) == -1) {
        ALOGE("*** sigprocmask failed: %s\n", strerror(errno));
        return false;
    }

    timespec ts = {.tv_sec = timeout_secs, .tv_nsec = 0};
    int ret = TEMP_FAILURE_RETRY(sigtimedwait(&child_mask, nullptr, &ts));
    int saved_errno = errno;

    // Set the signals back the way they were.
    if (sigprocmask(SIG_SETMASK, &old_mask, nullptr) == -1) {
        ALOGE("*** sigprocmask failed: %s\n", strerror(errno));
        if (ret == 0) {
            return false;
        }
    }
    if (ret == -1) {
        errno = saved_errno;
        if (errno == EAGAIN) {
            errno = ETIMEDOUT;
        } else {
            ALOGE("*** sigtimedwait failed: %s\n", strerror(errno));
        }
        return false;
    }

    pid_t child_pid = waitpid(pid, status, WNOHANG);
    if (child_pid != pid) {
        if (child_pid != -1) {
            ALOGE("*** Waiting for pid %d, got pid %d instead\n", pid, child_pid);
        } else {
            ALOGE("*** waitpid failed: %s\n", strerror(errno));
        }
        return false;
    }
    return true;
}

// Runs the given command. Kills the command if it does not finish by timeout.
int runCommand(int timeout_secs, const char* file, std::vector<const char*> args) {
    pid_t pid = fork();

    // handle error case
    if (pid < 0) {
        ALOGE("fork failed %s", strerror(errno));
        return pid;
    }

    // handle child case
    if (pid == 0) {
        /* make sure the child dies when parent dies */
        prctl(PR_SET_PDEATHSIG, SIGKILL);

        /* just ignore SIGPIPE, will go down with parent's */
        struct sigaction sigact;
        memset(&sigact, 0, sizeof(sigact));
        sigact.sa_handler = SIG_IGN;
        sigaction(SIGPIPE, &sigact, nullptr);

        execvp(file, (char**)args.data());
        // execvp's result will be handled after waitpid_with_timeout() below, but
        // if it failed, it's safer to exit dumpstate.
        ALOGE("execvp on command %s failed (error: %s)", file, strerror(errno));
        _exit(EXIT_FAILURE);
    }

    // handle parent case
    int status;
    bool ret = waitpid_with_timeout(pid, timeout_secs, &status);

    if (!ret) {
        if (errno == ETIMEDOUT) {
            ALOGE("command %s timed out (killing pid %d)", file, pid);
        } else {
            ALOGE("command %s: Error (killing pid %d)\n", file, pid);
        }
        kill(pid, SIGTERM);
        if (!waitpid_with_timeout(pid, 5, nullptr)) {
            kill(pid, SIGKILL);
            if (!waitpid_with_timeout(pid, 5, nullptr)) {
                ALOGE("could not kill command '%s' (pid %d) even with SIGKILL.\n", file, pid);
            }
        }
        return -1;
    }

    if (WIFSIGNALED(status)) {
        ALOGE("command '%s' failed: killed by signal %d\n", file, WTERMSIG(status));
    } else if (WIFEXITED(status) && WEXITSTATUS(status) > 0) {
        status = WEXITSTATUS(status);
        ALOGE("command '%s' failed: exit code %d\n", file, status);
    }

    return status;
}

void takeScreenshotForDisplayId(PhysicalDisplayId id, const char* tmp_dir,
        std::vector<std::string>* extra_files) {
    std::string id_as_string = to_string(id);
    std::string filename = std::string(tmp_dir) + kScreenshotPrefix + id_as_string + ".png";
    std::vector<const char*> args { "-p", "-d", id_as_string.c_str(), filename.c_str(), nullptr };
    ALOGI("capturing screen for display (%s) as %s", id_as_string.c_str(), filename.c_str());
    int status = runCommand(10, "/system/bin/screencap", args);
    if (status == 0) {
        LOG(INFO) << "Screenshot saved for display:" << id_as_string;
    }
    // add the file regardless of the exit status of the screencap util.
    extra_files->push_back(filename);

    LOG(ERROR) << "Failed to take screenshot for display:" << id_as_string;
}

void takeScreenshot(const char* tmp_dir, std::vector<std::string>* extra_files) {
    // Now send the screencaptures
    std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();

    for (PhysicalDisplayId display_id : ids) {
        takeScreenshotForDisplayId(display_id, tmp_dir, extra_files);
    }
}

bool recursiveRemoveDir(const std::string& path) {
    auto callback = [](const char* child, const struct stat*, int file_type, struct FTW*) -> int {
        if (file_type == FTW_DP) {
            if (rmdir(child) == -1) {
                ALOGE("rmdir(%s): %s", child, strerror(errno));
                return -1;
            }
        } else if (file_type == FTW_F) {
            if (unlink(child) == -1) {
                ALOGE("unlink(%s): %s", child, strerror(errno));
                return -1;
            }
        }
        return 0;
    };
    // do a file tree walk with a sufficiently large depth.
    return nftw(path.c_str(), callback, 128, FTW_DEPTH) == 0;
}

status_t createTempDir(const char* dir) {
    struct stat sb;
    if (TEMP_FAILURE_RETRY(stat(dir, &sb)) == 0) {
        if (!recursiveRemoveDir(dir)) {
            return -errno;
        }
    } else if (errno != ENOENT) {
        ALOGE("Failed to stat %s ", dir);
        return -errno;
    }
    if (TEMP_FAILURE_RETRY(mkdir(dir, 0700)) == -1) {
        ALOGE("Failed to mkdir %s", dir);
        return -errno;
    }
    return OK;
}

// Removes bugreport
void cleanupBugreportFile(const std::string& zip_path) {
    if (unlink(zip_path.c_str()) != 0) {
        ALOGE("Could not unlink %s (%s)", zip_path.c_str(), strerror(errno));
    }
}

}  // namespace

int main(void) {
    ALOGI("Starting bugreport collecting service");

    auto t0 = std::chrono::steady_clock::now();

    std::vector<std::string> extra_files;
    if (createTempDir(kTempDirectory) == OK) {
        // take screenshots of the physical displays as early as possible
        takeScreenshot(kTempDirectory, &extra_files);
    }

    // Start the dumpstatez service.
    android::base::SetProperty("ctl.start", "car-dumpstatez");

    size_t bytes_written = 0;

    std::string zip_path;
    int progress_socket = openSocket(kCarBrProgressSocket);
    if (progress_socket < 0) {
        // early out. in this case we will not print the final message, but that is ok.
        android::base::SetProperty("ctl.stop", "car-dumpstatez");
        return EXIT_FAILURE;
    }
    bool ret_val = doBugreport(progress_socket, &bytes_written, &zip_path);
    close(progress_socket);

    if (ret_val) {
        int output_socket = openSocket(kCarBrOutputSocket);
        if (output_socket != -1) {
            ret_val = copyFile(zip_path, output_socket);
            close(output_socket);
        }
    }

    int extra_output_socket = openSocket(kCarBrExtraOutputSocket);
    if (extra_output_socket != -1 && ret_val) {
        zipFilesToFd(extra_files, extra_output_socket);
    }
    if (extra_output_socket != -1) {
        close(extra_output_socket);
    }

    auto delta = std::chrono::duration_cast<std::chrono::duration<double>>(
                     std::chrono::steady_clock::now() - t0)
                     .count();

    std::string result = ret_val ? "success" : "failed";
    ALOGI("bugreport %s in %.02fs, %zu bytes written", result.c_str(), delta, bytes_written);
    cleanupBugreportFile(zip_path);

    recursiveRemoveDir(kTempDirectory);

    // No matter how doBugreport() finished, let's try to explicitly stop
    // car-dumpstatez in case it stalled.
    android::base::SetProperty("ctl.stop", "car-dumpstatez");

    return ret_val ? EXIT_SUCCESS : EXIT_FAILURE;
}
