/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.audio;

import static android.car.builtin.media.AudioManagerHelper.isCallFocusRequestClientId;
import static android.car.builtin.media.AudioManagerHelper.usageToString;
import static android.media.AudioManager.AUDIOFOCUS_FLAG_DELAY_OK;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.android.car.audio.CarAudioContext.isCriticalAudioAudioAttribute;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.oem.AudioFocusEntry;
import android.car.oem.OemCarAudioFocusResult;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.media.audiopolicy.AudioPolicy;
import android.util.ArrayMap;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.LocalLog;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class CarAudioFocus extends AudioPolicy.AudioPolicyFocusListener {

    private static final String TAG = CarLog.tagFor(CarAudioFocus.class);

    private static final int FOCUS_EVENT_LOGGER_QUEUE_SIZE = 25;

    private final AudioManager mAudioManager;
    private final PackageManager mPackageManager;
    private final CarVolumeInfoWrapper mCarVolumeInfoWrapper;
    private AudioPolicy mAudioPolicy; // Dynamically assigned just after construction

    private final LocalLog mFocusEventLogger;

    private final FocusInteraction mFocusInteraction;

    private final CarAudioContext mCarAudioContext;

    private AudioFocusInfo mDelayedRequest;

    // We keep track of all the focus requesters in this map, with their clientId as the key.
    // This is used both for focus dispatch and death handling
    // Note that the clientId reflects the AudioManager instance and listener object (if any)
    // so that one app can have more than one unique clientId by setting up distinct listeners.
    // Because the listener gets only LOSS/GAIN messages, this is important for an app to do if
    // it expects to request focus concurrently for different USAGEs so it knows which USAGE
    // gained or lost focus at any given moment.  If the SAME listener is used for requests of
    // different USAGE while the earlier request is still in the focus stack (whether holding
    // focus or pending), the new request will be REJECTED so as to avoid any confusion about
    // the meaning of subsequent GAIN/LOSS events (which would continue to apply to the focus
    // request that was already active or pending).
    private final ArrayMap<String, FocusEntry> mFocusHolders = new ArrayMap<>();
    private final ArrayMap<String, FocusEntry> mFocusLosers = new ArrayMap<>();

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mIsFocusRestricted;

    CarAudioFocus(AudioManager audioManager, PackageManager packageManager,
            FocusInteraction focusInteraction, CarAudioContext carAudioContext,
            CarVolumeInfoWrapper volumeInfoWrapper) {
        mAudioManager = Objects.requireNonNull(audioManager, "Audio manager can not be null");
        mPackageManager = Objects.requireNonNull(packageManager, "Package manager can not null");
        mFocusEventLogger = new LocalLog(FOCUS_EVENT_LOGGER_QUEUE_SIZE);
        mFocusInteraction = Objects.requireNonNull(focusInteraction,
                "Focus interactions can not be null");
        mCarAudioContext = Objects.requireNonNull(carAudioContext,
                "Car audio context can not be null");
        mCarVolumeInfoWrapper = Objects.requireNonNull(volumeInfoWrapper,
                "Car volume info can not be null");
    }


    // This has to happen after the construction to avoid a chicken and egg problem when setting up
    // the AudioPolicy which must depend on this object.
    public void setOwningPolicy(AudioPolicy parentPolicy) {
        mAudioPolicy = parentPolicy;
    }

    void setRestrictFocus(boolean isFocusRestricted) {
        logFocusEvent("setRestrictFocus: is focus restricted " + isFocusRestricted);
        synchronized (mLock) {
            mIsFocusRestricted = isFocusRestricted;
            if (mIsFocusRestricted) {
                abandonNonCriticalFocusLocked();
            }
        }
    }

    @GuardedBy("mLock")
    private void abandonNonCriticalFocusLocked() {
        if (mDelayedRequest != null) {
            if (!isCriticalAudioAudioAttribute(mDelayedRequest.getAttributes())) {
                logFocusEvent(
                        "abandonNonCriticalFocusLocked abandoning non critical delayed request "
                                + mDelayedRequest);
                sendFocusLossLocked(mDelayedRequest, AUDIOFOCUS_LOSS);
                mDelayedRequest = null;
            } else {
                logFocusEvent("abandonNonCriticalFocusLocked keeping critical delayed request "
                                + mDelayedRequest);
            }
        }

        abandonNonCriticalEntriesLocked(mFocusLosers);
        abandonNonCriticalEntriesLocked(mFocusHolders);
    }

    @GuardedBy("mLock")
    private void abandonNonCriticalEntriesLocked(Map<String, FocusEntry> entries) {
        List<String> clientsToRemove = new ArrayList<>();
        for (FocusEntry holderEntry : entries.values()) {
            if (isCriticalAudioAudioAttribute(holderEntry.getAudioFocusInfo().getAttributes())) {
                Slogf.i(TAG, "abandonNonCriticalEntriesLocked keeping critical focus "
                        + holderEntry);
                continue;
            }

            sendFocusLossLocked(holderEntry.getAudioFocusInfo(), AUDIOFOCUS_LOSS);
            clientsToRemove.add(holderEntry.getAudioFocusInfo().getClientId());
        }

        for (int i = 0; i < clientsToRemove.size(); i++) {
            String clientId = clientsToRemove.get(i);
            FocusEntry removedEntry = entries.remove(clientId);
            removeBlockerAndRestoreUnblockedWaitersLocked(removedEntry);
        }
    }

    // This sends a focus loss message to the targeted requester.
    private void sendFocusLossLocked(AudioFocusInfo loser, int lossType) {
        int result = mAudioManager.dispatchAudioFocusChange(loser, lossType,
                mAudioPolicy);
        if (result != AUDIOFOCUS_REQUEST_GRANTED) {
            // TODO:  Is this actually an error, or is it okay for an entry in the focus stack
            // to NOT have a listener?  If that's the case, should we even keep it in the focus
            // stack?
            Slogf.e(TAG, "Failure to signal loss of audio focus with error: " + result);
        }

        logFocusEvent("sendFocusLoss for client " + loser.getClientId()
                + " with loss type " + focusEventToString(lossType)
                + " resulted in " + focusRequestResponseToString(result));
    }

    /** @see AudioManager#requestAudioFocus(AudioManager.OnAudioFocusChangeListener, int, int, int) */
    // Note that we replicate most, but not all of the behaviors of the default MediaFocusControl
    // engine as of Android P.
    // Besides the interaction matrix which allows concurrent focus for multiple requestors, which
    // is the reason for this module, we also treat repeated requests from the same clientId
    // slightly differently.
    // If a focus request for the same listener (clientId) is received while that listener is
    // already in the focus stack, we REJECT it outright unless it is for the same USAGE.
    // If it is for the same USAGE, we replace the old request with the new one.
    // The default audio framework's behavior is to remove the previous entry in the stack (no-op
    // if the requester is already holding focus).
    @GuardedBy("mLock")
    private int evaluateFocusRequestLocked(AudioFocusInfo afi) {
        Slogf.i(TAG, "Evaluating " + focusEventToString(afi.getGainRequest())
                + " request for client " + afi.getClientId()
                + " with usage " + usageToString(afi.getAttributes().getUsage()));

        if (mIsFocusRestricted) {
            if (!isCriticalAudioAudioAttribute(afi.getAttributes())) {
                return AUDIOFOCUS_REQUEST_FAILED;
            }
        }

        // Is this a request for permanent focus?
        // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -- Means Notifications should be denied
        // AUDIOFOCUS_GAIN_TRANSIENT -- Means current focus holders should get transient loss
        // AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -- Means other can duck (no loss message from us)
        // NOTE:  We expect that in practice it will be permanent for all media requests and
        //        transient for everything else, but that isn't currently an enforced requirement.
        boolean permanent = (afi.getGainRequest() == AUDIOFOCUS_GAIN);
        boolean allowDucking = (afi.getGainRequest() == AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

        int requestedContext = mCarAudioContext.getContextForAttributes(afi.getAttributes());

        boolean allowDelayedFocus = canReceiveDelayedFocus(afi);

        // We don't allow sharing listeners (client IDs) between two concurrent requests
        // (because the app would have no way to know to which request a later event applied)
        if (mDelayedRequest != null && afi.getClientId().equals(mDelayedRequest.getClientId())) {
            int delayedRequestedContext = mCarAudioContext.getContextForAttributes(
                    mDelayedRequest.getAttributes());
            // If it is for a different context then reject
            if (delayedRequestedContext != requestedContext) {
                // Trivially reject a request for a different USAGE
                Slogf.e(TAG, "Client %s has already delayed requested focus for %s - cannot request"
                        + " focus for %s on same listener.", mDelayedRequest.getClientId(),
                        usageToString(mDelayedRequest.getAttributes().getUsage()),
                        usageToString(afi.getAttributes().getUsage()));
                return AUDIOFOCUS_REQUEST_FAILED;
            }
        }

        // These entries have permanently lost focus as a result of this request, so they
        // should be removed from all blocker lists.
        ArrayList<FocusEntry> permanentlyLost = new ArrayList<>();

        OemCarAudioFocusResult evaluationResults =
                evaluateFocusRequestLocked(permanentlyLost, afi,
                        allowDucking, allowDelayedFocus);

        if (evaluationResults.equals(OemCarAudioFocusResult.EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS)) {
            return AUDIOFOCUS_REQUEST_FAILED;
        }

        // Now that we've decided we'll grant focus, construct our new FocusEntry
        AudioFocusEntry focusEntry = evaluationResults.getAudioFocusEntry();
        FocusEntry newEntry = new FocusEntry(focusEntry.getAudioFocusInfo(),
                focusEntry.getAudioContextId(), mPackageManager);

        // Now that we're sure we'll accept this request, update any requests which we would
        // block but are already out of focus but waiting to come back
        List<AudioFocusEntry> blocked = evaluationResults.getNewlyBlockedAudioFocusEntries();
        for (int index = 0; index < blocked.size(); index++) {
            AudioFocusEntry newlyBlocked = blocked.get(index);
            FocusEntry entry = mFocusLosers.get(newlyBlocked.getAudioFocusInfo().getClientId());
            // If we're out of focus it must be because somebody is blocking us
            assert !entry.isUnblocked();

            if (permanent) {
                // This entry has now lost focus forever
                sendFocusLossLocked(entry.getAudioFocusInfo(), AUDIOFOCUS_LOSS);
                entry.setDucked(false);
                FocusEntry deadEntry = mFocusLosers.remove(
                        entry.getAudioFocusInfo().getClientId());
                assert deadEntry != null;
                permanentlyLost.add(entry);
            } else {
                if (!allowDucking && entry.isDucked()) {
                    // This entry was previously allowed to duck, but can no longer do so.
                    Slogf.i(TAG, "Converting duckable loss to non-duckable for "
                            + entry.getClientId());
                    sendFocusLossLocked(entry.getAudioFocusInfo(), AUDIOFOCUS_LOSS_TRANSIENT);
                    entry.setDucked(false);
                }
                // Note that this new request is yet one more reason we can't (yet) have focus
                entry.addBlocker(newEntry);
            }
        }

        // Notify and update any requests which are now losing focus as a result of the new request
        List<AudioFocusEntry> loss = evaluationResults.getNewlyLossAudioFocusEntries();
        for (int index = 0; index < loss.size(); index++) {
            AudioFocusEntry newlyLoss = loss.get(index);
            FocusEntry entry = mFocusHolders.get(newlyLoss.getAudioFocusInfo().getClientId());
            // If we have focus (but are about to loose it), nobody should be blocking us yet
            assert entry.isUnblocked();

            int lossType;
            if (permanent) {
                lossType = AUDIOFOCUS_LOSS;
            } else if (allowDucking && entry.receivesDuckEvents()) {
                lossType = AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
                entry.setDucked(true);
            } else {
                lossType = AUDIOFOCUS_LOSS_TRANSIENT;
            }
            sendFocusLossLocked(entry.getAudioFocusInfo(), lossType);

            // The entry no longer holds focus, so take it out of the holders list
            mFocusHolders.remove(entry.getAudioFocusInfo().getClientId());

            if (permanent) {
                permanentlyLost.add(entry);
            } else {
                // Add ourselves to the list of requests waiting to get focus back and
                // note why we lost focus so we can tell when it's time to get it back
                mFocusLosers.put(entry.getAudioFocusInfo().getClientId(), entry);
                entry.addBlocker(newEntry);
            }
        }

        if (evaluationResults.getAudioFocusResult() != AUDIOFOCUS_REQUEST_DELAYED) {
            // If the entry is replacing an existing one, and if a delayed Request is pending
            // this replaced entry is not a blocker of the delayed.
            // So add it before reconsidering the delayed.
            mFocusHolders.put(afi.getClientId(), newEntry);
        }

        // Now that all new blockers have been added, clear out any other requests that have been
        // permanently lost as a result of this request. Treat them as abandoned - if they're on
        // any blocker lists, remove them. If any focus requests become unblocked as a result,
        // re-grant them. (This can happen when a GAIN_TRANSIENT_MAY_DUCK request replaces a
        // GAIN_TRANSIENT request from the same listener.)
        for (int index = 0; index < permanentlyLost.size(); index++) {
            FocusEntry entry = permanentlyLost.get(index);
            Slogf.d(TAG, "Cleaning up entry " + entry.getClientId());
            removeBlockerAndRestoreUnblockedWaitersLocked(entry);
        }

        if (evaluationResults.getAudioFocusResult() == AUDIOFOCUS_REQUEST_DELAYED) {
            swapDelayedAudioFocusRequestLocked(afi);
            return AUDIOFOCUS_REQUEST_DELAYED;
        }

        Slogf.i(TAG, "AUDIOFOCUS_REQUEST_GRANTED");
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    @GuardedBy("mLock")
    private OemCarAudioFocusResult evaluateFocusRequestLocked(
            ArrayList<FocusEntry> permanentlyLost,
            AudioFocusInfo audioFocusInfo, boolean allowDucking,
            boolean allowDelayedFocus) {

        int requestedContext =
                mCarAudioContext.getContextForAttributes(audioFocusInfo.getAttributes());
        FocusEvaluation holdersEvaluation = evaluateAgainstFocusHoldersLocked(audioFocusInfo,
                requestedContext, allowDucking, allowDelayedFocus);

        if (holdersEvaluation.equals(FocusEvaluation.FOCUS_EVALUATION_FAILED)) {
            return OemCarAudioFocusResult.EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;
        }

        FocusEvaluation losersEvaluation = evaluateAgainstFocusLosersLocked(audioFocusInfo,
                requestedContext, allowDucking, allowDelayedFocus);

        if (losersEvaluation.equals(FocusEvaluation.FOCUS_EVALUATION_FAILED)) {
            return OemCarAudioFocusResult.EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;
        }

        if (holdersEvaluation.mReplacedEntry != null) {
            mFocusHolders.remove(holdersEvaluation.mReplacedEntry.getClientId());
            permanentlyLost.add(holdersEvaluation.mReplacedEntry);
        }
        if (losersEvaluation.mReplacedEntry != null) {
            mFocusLosers.remove(losersEvaluation.mReplacedEntry.getClientId());
            permanentlyLost.add(losersEvaluation.mReplacedEntry);
        }

        boolean delayFocus = holdersEvaluation.mAudioFocusEvalResults == AUDIOFOCUS_REQUEST_DELAYED
                || losersEvaluation.mAudioFocusEvalResults == AUDIOFOCUS_REQUEST_DELAYED;

        int results = delayFocus ? AUDIOFOCUS_REQUEST_DELAYED : AUDIOFOCUS_REQUEST_GRANTED;

        AudioFocusEntry focusEntry =
                new AudioFocusEntry.Builder(audioFocusInfo, requestedContext,
                getVolumeGroupForAttribute(audioFocusInfo.getAttributes()),
                AudioManager.AUDIOFOCUS_GAIN).build();

        return new OemCarAudioFocusResult.Builder(
                convertAudioFocusEntries(holdersEvaluation.mChangedEntries),
                convertAudioFocusEntries(losersEvaluation.mChangedEntries),
                results).setAudioFocusEntry(focusEntry)
                .build();
    }

    private List<AudioFocusEntry> convertAudioFocusEntries(List<FocusEntry> changedEntries) {
        List<AudioFocusEntry> audioFocusEntries = new ArrayList<>(changedEntries.size());
        for (int index = 0; index < changedEntries.size(); index++) {
            AudioFocusInfo info = changedEntries.get(index).getAudioFocusInfo();
            audioFocusEntries.add(new AudioFocusEntry.Builder(info,
                    mCarAudioContext.getContextForAudioAttribute(info.getAttributes()),
                    getVolumeGroupForAttribute(info.getAttributes()),
                    AUDIOFOCUS_LOSS_TRANSIENT).build());
        }
        return audioFocusEntries;
    }

    private int getVolumeGroupForAttribute(AudioAttributes attributes) {
        //TODO(b/240615622): Get volume group info from service
        return 0;
    }

    @GuardedBy("mLock")
    private FocusEvaluation evaluateAgainstFocusLosersLocked(AudioFocusInfo afi,
            int requestedContext, boolean allowDucking, boolean allowDelayedFocus) {
        Slogf.i(TAG, "Scanning those who've already lost focus...");
        boolean delayFocusForCurrentRequest = false;
        FocusEntry replacedBlockedEntry = null;
        ArrayList<FocusEntry> blocked = new ArrayList<FocusEntry>();
        for (FocusEntry entry : mFocusLosers.values()) {
            Slogf.i(TAG, entry.getAudioFocusInfo().getClientId());

            // If this request is for Notifications and a pending focus holder has specified
            // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, then reject the request
            if ((CarAudioContext.isNotificationAudioAttribute(afi.getAttributes()))
                    && (entry.getAudioFocusInfo().getGainRequest()
                    == AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)) {
                return FocusEvaluation.FOCUS_EVALUATION_FAILED;
            }

            // We don't allow sharing listeners (client IDs) between two concurrent requests
            // (because the app would have no way to know to which request a later event applied)
            if (afi.getClientId().equals(entry.getAudioFocusInfo().getClientId())) {
                if (entry.getAudioContext() == requestedContext) {
                    // This is a repeat of a request that is currently blocked.
                    // Evaluate it as if it were a new request, but note that we should remove
                    // the old pending request, and move it.
                    // We do not want to evaluate the new request against itself.
                    Slogf.i(TAG, "Replacing pending request from same client");
                    replacedBlockedEntry = entry;
                    continue;
                } else {
                    // Trivially reject a request for a different USAGE
                    Slogf.e(TAG, "Client %s has already requested focus for %s - cannot request "
                                    + "focus for %s on same listener.", entry.getClientId(),
                            usageToString(entry.getAudioFocusInfo().getAttributes().getUsage()),
                            usageToString(afi.getAttributes().getUsage()));
                    return FocusEvaluation.FOCUS_EVALUATION_FAILED;
                }
            }

            int interactionResult = mFocusInteraction
                    .evaluateRequest(requestedContext, entry, blocked, allowDucking,
                            allowDelayedFocus);
            if (interactionResult == AUDIOFOCUS_REQUEST_FAILED) {
                return FocusEvaluation.FOCUS_EVALUATION_FAILED;
            }
            if (interactionResult == AUDIOFOCUS_REQUEST_DELAYED) {
                delayFocusForCurrentRequest = true;
            }
        }
        int results = delayFocusForCurrentRequest
                ? AUDIOFOCUS_REQUEST_DELAYED : AUDIOFOCUS_REQUEST_GRANTED;
        return new FocusEvaluation(blocked, replacedBlockedEntry, results);
    }

    @GuardedBy("mLock")
    private FocusEvaluation evaluateAgainstFocusHoldersLocked(AudioFocusInfo afi,
            int requestedContext, boolean allowDucking, boolean allowDelayedFocus) {
        // Scan all active and pending focus requests.  If any should cause rejection of
        // this new request, then we're done.  Keep a list of those against whom we're exclusive
        // so we can update the relationships if/when we are sure we won't get rejected.
        Slogf.i(TAG, "Scanning focus holders...");
        boolean delayFocusForCurrentRequest = false;
        FocusEntry replacedCurrentEntry = null;
        ArrayList<FocusEntry> losers = new ArrayList<FocusEntry>();
        for (FocusEntry entry : mFocusHolders.values()) {
            Slogf.d(TAG, "Evaluating focus holder: " + entry.getClientId());

            // If this request is for Notifications and a current focus holder has specified
            // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, then reject the request.
            // This matches the hardwired behavior in the default audio policy engine which apps
            // might expect (The interaction matrix doesn't have any provision for dealing with
            // override flags like this).
            if (CarAudioContext.isNotificationAudioAttribute(afi.getAttributes())
                    && (entry.getAudioFocusInfo().getGainRequest()
                    == AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)) {
                return FocusEvaluation.FOCUS_EVALUATION_FAILED;
            }

            // We don't allow sharing listeners (client IDs) between two concurrent requests
            // (because the app would have no way to know to which request a later event applied)
            if (afi.getClientId().equals(entry.getAudioFocusInfo().getClientId())) {
                if ((entry.getAudioContext() == requestedContext)
                        || canSwapCallOrRingerClientRequest(afi.getClientId(),
                        entry.getAudioFocusInfo().getAttributes(), afi.getAttributes())) {
                    // This is a request from a current focus holder.
                    // Abandon the previous request (without sending a LOSS notification to it),
                    // and don't check the interaction matrix for it.
                    Slogf.i(TAG, "Replacing accepted request from same client: %s",
                            afi.getClientId());
                    replacedCurrentEntry = entry;
                    continue;
                } else {
                    // Trivially reject a request for a different USAGE
                    Slogf.e(TAG, "Client %s has already requested focus for %s - cannot request "
                                    + "focus for %s on same listener.", entry.getClientId(),
                            usageToString(entry.getAudioFocusInfo().getAttributes().getUsage()),
                            usageToString(afi.getAttributes().getUsage()));
                    return FocusEvaluation.FOCUS_EVALUATION_FAILED;
                }
            }

            int interactionResult = mFocusInteraction.evaluateRequest(requestedContext, entry,
                    losers, allowDucking, allowDelayedFocus);
            if (interactionResult == AUDIOFOCUS_REQUEST_FAILED) {
                return FocusEvaluation.FOCUS_EVALUATION_FAILED;
            }
            if (interactionResult == AUDIOFOCUS_REQUEST_DELAYED) {
                delayFocusForCurrentRequest = true;
            }
        }

        int results = delayFocusForCurrentRequest
                ? AUDIOFOCUS_REQUEST_DELAYED : AUDIOFOCUS_REQUEST_GRANTED;
        return new FocusEvaluation(losers, replacedCurrentEntry, results);
    }

    private static boolean canSwapCallOrRingerClientRequest(String clientId,
            AudioAttributes currentAttributes, AudioAttributes requestedAttributes) {
        return isCallFocusRequestClientId(clientId)
                && isRingerOrCallAudioAttributes(currentAttributes)
                && isRingerOrCallAudioAttributes(requestedAttributes);
    }

    private static boolean isRingerOrCallAudioAttributes(AudioAttributes attributes) {
        return CarAudioContext.isRingerOrCallAudioAttribute(attributes);
    }

    @Override
    public void onAudioFocusRequest(AudioFocusInfo afi, int requestResult) {
        int response;
        AudioPolicy policy;
        synchronized (mLock) {
            policy = mAudioPolicy;
            response = evaluateFocusRequestLocked(afi);
        }

        // Post our reply for delivery to the original focus requester
        mAudioManager.setFocusRequestResult(afi, response, policy);
        logFocusEvent("onAudioFocusRequest for client " + afi.getClientId()
                + " with gain type " + focusEventToString(afi.getGainRequest())
                + " resulted in " + focusRequestResponseToString(response));
    }

    private void swapDelayedAudioFocusRequestLocked(AudioFocusInfo afi) {
        // If we are swapping to a different client then send the focus loss signal
        if (mDelayedRequest != null
                && !afi.getClientId().equals(mDelayedRequest.getClientId())) {
            sendFocusLossLocked(mDelayedRequest, AUDIOFOCUS_LOSS);
        }
        mDelayedRequest = afi;
    }

    private boolean canReceiveDelayedFocus(AudioFocusInfo afi) {
        if (afi.getGainRequest() != AUDIOFOCUS_GAIN) {
            return false;
        }
        return (afi.getFlags() & AUDIOFOCUS_FLAG_DELAY_OK) == AUDIOFOCUS_FLAG_DELAY_OK;
    }

    /**
     * @see AudioManager#abandonAudioFocus(AudioManager.OnAudioFocusChangeListener, AudioAttributes)
     * Note that we'll get this call for a focus holder that dies while in the focus stack, so
     * we don't need to watch for death notifications directly.
     * */
    @Override
    public void onAudioFocusAbandon(AudioFocusInfo afi) {
        logFocusEvent("onAudioFocusAbandon for client " + afi.getClientId());
        synchronized (mLock) {
            FocusEntry deadEntry = removeFocusEntryLocked(afi);

            if (deadEntry != null) {
                removeBlockerAndRestoreUnblockedWaitersLocked(deadEntry);
            } else {
                removeDelayedAudioFocusRequestLocked(afi);
            }
        }
    }

    private void removeDelayedAudioFocusRequestLocked(AudioFocusInfo afi) {
        if (mDelayedRequest != null && afi.getClientId().equals(mDelayedRequest.getClientId())) {
            mDelayedRequest = null;
        }
    }

    /**
     * Remove Focus entry from focus holder or losers entry lists
     * @param afi Audio Focus Info to remove
     * @return Removed Focus Entry
     */
    private FocusEntry removeFocusEntryLocked(AudioFocusInfo afi) {
        Slogf.i(TAG, "removeFocusEntry " + afi.getClientId());

        // Remove this entry from our active or pending list
        FocusEntry deadEntry = mFocusHolders.remove(afi.getClientId());
        if (deadEntry == null) {
            deadEntry = mFocusLosers.remove(afi.getClientId());
            if (deadEntry == null) {
                // Caller is providing an unrecognzied clientId!?
                Slogf.w(TAG, "Audio focus abandoned by unrecognized client id: "
                        + afi.getClientId());
                // This probably means an app double released focused for some reason.  One
                // harmless possibility is a race between an app being told it lost focus and the
                // app voluntarily abandoning focus.  More likely the app is just sloppy.  :)
                // The more nefarious possibility is that the clientId is actually corrupted
                // somehow, in which case we might have a real focus entry that we're going to fail
                // to remove. If that were to happen, I'd expect either the app to swallow it
                // silently, or else take unexpected action (eg: resume playing spontaneously), or
                // else to see "Failure to signal ..." gain/loss error messages in the log from
                // this module when a focus change tries to take action on a truly zombie entry.
            }
        }
        return deadEntry;
    }

    @GuardedBy("mLock")
    private void removeBlockerAndRestoreUnblockedWaitersLocked(FocusEntry deadEntry) {
        attemptToGainFocusForDelayedAudioFocusRequestLocked();
        removeBlockerAndRestoreUnblockedFocusLosersLocked(deadEntry);
    }

    @GuardedBy("mLock")
    private void attemptToGainFocusForDelayedAudioFocusRequestLocked() {
        if (mDelayedRequest == null) {
            return;
        }
        // Prevent cleanup of permanent lost to recall attemptToGainFocusForDelayedAudioFocusRequest
        // Whatever granted / denied / delayed again, no need to restore, mDelayedRequest restored
        // if delayed again.
        AudioFocusInfo delayedFocusInfo = mDelayedRequest;
        mDelayedRequest = null;
        int delayedFocusRequestResults = evaluateFocusRequestLocked(delayedFocusInfo);
        if (delayedFocusRequestResults == AUDIOFOCUS_REQUEST_GRANTED) {
            FocusEntry focusEntry = mFocusHolders.get(delayedFocusInfo.getClientId());
            if (dispatchFocusGainedLocked(focusEntry.getAudioFocusInfo())
                    == AUDIOFOCUS_REQUEST_FAILED) {
                Slogf.e(TAG, "Failure to signal gain of audio focus gain for "
                        + "delayed focus clientId " + focusEntry.getClientId());
                mFocusHolders.remove(focusEntry.getClientId());
                removeBlockerFromBlockedFocusLosersLocked(focusEntry);
                sendFocusLossLocked(focusEntry.getAudioFocusInfo(),
                        AUDIOFOCUS_LOSS);
                logFocusEvent("Did not gained delayed audio focus for "
                        + focusEntry.getClientId());
            }
        } else if (delayedFocusRequestResults == AUDIOFOCUS_REQUEST_FAILED) {
            // Delayed request has permanently be denied
            logFocusEvent("Delayed audio focus retry failed for " + delayedFocusInfo.getClientId());
            sendFocusLossLocked(delayedFocusInfo, AUDIOFOCUS_LOSS);
        } else {
            assert mDelayedRequest.equals(delayedFocusInfo);
        }
    }

    /**
     * Removes the dead entry from blocked waiters but does not send focus gain signal
     */
    private void removeBlockerFromBlockedFocusLosersLocked(FocusEntry deadEntry) {
        // Remove this entry from the blocking list of any pending requests
        Iterator<FocusEntry> it = mFocusLosers.values().iterator();
        while (it.hasNext()) {
            FocusEntry entry = it.next();
            // Remove the retiring entry from all blocker lists
            entry.removeBlocker(deadEntry);
        }
    }

    /**
     * Removes the dead entry from blocked waiters and sends focus gain signal
     */
    private void removeBlockerAndRestoreUnblockedFocusLosersLocked(FocusEntry deadEntry) {
        // Remove this entry from the blocking list of any pending requests
        Iterator<FocusEntry> it = mFocusLosers.values().iterator();
        while (it.hasNext()) {
            FocusEntry entry = it.next();

            // Remove the retiring entry from all blocker lists
            entry.removeBlocker(deadEntry);

            // Any entry whose blocking list becomes empty should regain focus
            if (entry.isUnblocked()) {
                Slogf.i(TAG, "Restoring unblocked entry " + entry.getClientId());
                // Pull this entry out of the focus losers list
                it.remove();

                // Add it back into the focus holders list
                mFocusHolders.put(entry.getClientId(), entry);

                dispatchFocusGainedLocked(entry.getAudioFocusInfo());
            }
        }
    }

    /**
     * Dispatch focus gain
     * @param afi Audio focus info
     * @return {@link AUDIOFOCUS_REQUEST_GRANTED} if focus is dispatched successfully
     */
    private int dispatchFocusGainedLocked(AudioFocusInfo afi) {
        // Send the focus (re)gain notification
        int result = mAudioManager.dispatchAudioFocusChange(afi, AUDIOFOCUS_GAIN, mAudioPolicy);
        if (result != AUDIOFOCUS_REQUEST_GRANTED) {
            // TODO:  Is this actually an error, or is it okay for an entry in the focus
            // stack to NOT have a listener?  If that's the case, should we even keep
            // it in the focus stack?
            Slogf.e(TAG, "Failure to signal gain of audio focus with error: " + result);
        }

        logFocusEvent("dispatchFocusGainedLocked for client " + afi.getClientId()
                        + " with gain type " + focusEventToString(afi.getGainRequest())
                        + " resulted in " + focusRequestResponseToString(result));
        return result;
    }

    /**
     * Query the current list of focus loser for uid
     * @param uid uid to query current focus loser
     * @return list of current focus losers for uid
     */
    ArrayList<AudioFocusInfo> getAudioFocusLosersForUid(int uid) {
        return getAudioFocusListForUid(uid, mFocusLosers);
    }

    /**
     * Query the current list of focus holders for uid
     * @param uid uid to query current focus holders
     * @return list of current focus holders that for uid
     */
    ArrayList<AudioFocusInfo> getAudioFocusHoldersForUid(int uid) {
        return getAudioFocusListForUid(uid, mFocusHolders);
    }

    List<AudioFocusInfo> getAudioFocusHolders() {
        return getAudioFocusInfos(mFocusHolders);
    }

    List<AudioFocusInfo> getAudioFocusLosers() {
        return getAudioFocusInfos(mFocusLosers);
    }

    private List<AudioFocusInfo> getAudioFocusInfos(ArrayMap<String, FocusEntry> focusEntries) {
        synchronized (mLock) {
            List<AudioFocusInfo> focusInfos = new ArrayList<>(focusEntries.size());
            for (int index = 0; index < focusEntries.size(); index++) {
                focusInfos.add(focusEntries.valueAt(index).getAudioFocusInfo());
            }
            return focusInfos;
        }
    }

    /**
     * Query input list for matching uid
     * @param uid uid to match in map
     * @param mapToQuery map to query for uid info
     * @return list of audio focus info that match uid
     */
    private ArrayList<AudioFocusInfo> getAudioFocusListForUid(int uid,
            Map<String, FocusEntry> mapToQuery) {
        ArrayList<AudioFocusInfo> matchingInfoList = new ArrayList<>();
        synchronized (mLock) {
            for (String clientId : mapToQuery.keySet()) {
                AudioFocusInfo afi = mapToQuery.get(clientId).getAudioFocusInfo();
                if (afi.getClientUid() == uid) {
                    matchingInfoList.add(afi);
                }
            }
        }
        return matchingInfoList;
    }

    /**
     * Remove the audio focus info, if entry is still active
     * dispatch lose focus transient to listeners
     * @param afi Audio Focus info to remove
     */
    void removeAudioFocusInfoAndTransientlyLoseFocus(AudioFocusInfo afi) {
        synchronized (mLock) {
            FocusEntry deadEntry = removeFocusEntryLocked(afi);
            if (deadEntry != null) {
                sendFocusLossLocked(deadEntry.getAudioFocusInfo(),
                        AUDIOFOCUS_LOSS_TRANSIENT);
                removeBlockerAndRestoreUnblockedWaitersLocked(deadEntry);
            }
        }
    }

    /**
     * Reevaluate focus request and regain focus
     * @param afi audio focus info to reevaluate
     * @return {@link AUDIOFOCUS_REQUEST_GRANTED} if focus is granted
     */
    int reevaluateAndRegainAudioFocus(AudioFocusInfo afi) {
        int results;
        synchronized (mLock) {
            results = evaluateFocusRequestLocked(afi);
            if (results == AUDIOFOCUS_REQUEST_GRANTED) {
                results = dispatchFocusGainedLocked(afi);
            }
        }

        return results;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("*CarAudioFocus*");
            writer.increaseIndent();
            writer.printf("Is focus restricted? %b\n", mIsFocusRestricted);
            writer.println();
            mFocusInteraction.dump(writer);

            writer.println("Current Focus Holders:");
            writer.increaseIndent();
            for (String clientId : mFocusHolders.keySet()) {
                mFocusHolders.get(clientId).dump(writer);
            }
            writer.decreaseIndent();

            writer.println("Transient Focus Losers:");
            writer.increaseIndent();
            for (String clientId : mFocusLosers.keySet()) {
                mFocusLosers.get(clientId).dump(writer);
            }
            writer.decreaseIndent();

            writer.printf("Queued Delayed Focus: %s\n",
                    mDelayedRequest == null ? "None" : mDelayedRequest.getClientId());

            writer.println("Focus Events:");
            writer.increaseIndent();
            mFocusEventLogger.dump(writer);
            writer.decreaseIndent();

            writer.decreaseIndent();
        }
    }

    private static String focusEventToString(int focusEvent) {
        switch (focusEvent) {
            case AUDIOFOCUS_GAIN:
                return "GAIN";
            case AUDIOFOCUS_GAIN_TRANSIENT:
                return "GAIN_TRANSIENT";
            case AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                return "GAIN_TRANSIENT_EXCLUSIVE";
            case AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                return "GAIN_TRANSIENT_MAY_DUCK";
            case AUDIOFOCUS_LOSS:
                return "LOSS";
            case AUDIOFOCUS_LOSS_TRANSIENT:
                return "LOSS_TRANSIENT";
            case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                return "LOSS_TRANSIENT_CAN_DUCK";
            default:
                return "unknown event " + focusEvent;
        }
    }

    private static String focusRequestResponseToString(int response) {
        if (response == AUDIOFOCUS_REQUEST_GRANTED) {
            return "REQUEST_GRANTED";
        } else if (response == AUDIOFOCUS_REQUEST_FAILED) {
            return "REQUEST_FAILED";
        }
        return "REQUEST_DELAYED";
    }

    private void logFocusEvent(String log) {
        mFocusEventLogger.log(log);
        Slogf.i(TAG, log);
    }

    /**
     * Returns the focus interaction for this car focus instance.
     */
    public FocusInteraction getFocusInteraction() {
        return mFocusInteraction;
    }

    private static final class FocusEvaluation {

        private static final FocusEvaluation FOCUS_EVALUATION_FAILED =
                new FocusEvaluation(/* changedEntries= */ new ArrayList<>(/* initialCap= */ 0),
                        /* replacedEntry= */ null, AUDIOFOCUS_REQUEST_FAILED);

        private final List<FocusEntry> mChangedEntries;
        @Nullable
        private final FocusEntry mReplacedEntry;
        private final int mAudioFocusEvalResults;

        FocusEvaluation(List<FocusEntry> changedEntries,
                @Nullable FocusEntry replacedEntry, int audioFocusEvalResults) {
            mChangedEntries = changedEntries;
            mReplacedEntry = replacedEntry;
            mAudioFocusEvalResults = audioFocusEvalResults;
        }

        @Override
        @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
        public String toString() {
            return new StringBuilder().append("{Changed Entries: ").append(mChangedEntries)
                    .append(", Replace Entry: ").append(mReplacedEntry)
                    .append(", Results: ").append(mAudioFocusEvalResults)
                    .append(" }").toString();
        }
    }
}
