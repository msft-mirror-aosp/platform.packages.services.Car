/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.automotive

import android.app.ActivityManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT
import android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.graphics.Rect
import android.content.Context
import android.os.IBinder
import android.util.Log
import android.util.Slog
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import javax.inject.Inject

const val TAG = "AutoTaskStackController"

@WMSingleton
class AutoTaskStackControllerImpl @Inject constructor(
    val taskOrganizer: ShellTaskOrganizer,
    @ShellMainThread private val shellMainThread: ShellExecutor,
    val transitions: Transitions,
    val shellInit: ShellInit,
    val rootTdaOrganizer: RootTaskDisplayAreaOrganizer,
    val context: Context,
    val autoTaskRepository: AutoTaskRepository
) : AutoTaskStackController, Transitions.TransitionHandler {
    override var autoTransitionHandlerDelegate: AutoTaskStackTransitionHandlerDelegate? = null
    override val taskStackStateMap = mutableMapOf<Int, AutoTaskStackState>()

    private val DBG = Log.isLoggable(TAG, Log.DEBUG)
    private val taskStackMap = mutableMapOf<Int, AutoTaskStack>()
    private val pendingTransitions = ArrayList<PendingTransition>()
    private val mTaskStackStateTranslator = TaskStackStateTranslator()
    private val appTasksMap = mutableMapOf<Int, ActivityManager.RunningTaskInfo>()
    private val defaultRootTaskPerDisplay = mutableMapOf<Int, Int>()

    init {
        shellInit.addInitCallback(this::onInit, this)
    }

    fun onInit() {
        transitions.addHandler(this)
        // TODO(b/392757141): Add a listener to get all the tasks instead of modifying the
        // RootTaskStackListenerAdapter
    }

    /** Translates the [AutoTaskStackState] to relevant WM and surface transactions. */
    inner class TaskStackStateTranslator {
        // TODO(b/384946072): Move to an interface with 2 implementations, one for root task and
        //  other for TDA
        fun applyVisibilityAndBounds(
            wct: WindowContainerTransaction,
            taskStack: AutoTaskStack,
            state: AutoTaskStackState
        ) {
            if (taskStack !is RootTaskStack) {
                Slog.e(TAG, "Unsupported task stack, unable to convertToWct")
                return
            }
            wct.setBounds(taskStack.rootTaskInfo.token, state.bounds)
            wct.reorder(taskStack.rootTaskInfo.token, state.childrenTasksVisible)
        }

        fun reorderLeash(
            taskStack: AutoTaskStack,
            state: AutoTaskStackState,
            transaction: Transaction
        ) {
            if (taskStack !is RootTaskStack) {
                Slog.e(TAG, "Unsupported task stack, unable to reorder leash")
                return
            }
            Slog.d(TAG, "Setting the layer ${state.layer}")
            transaction.setLayer(taskStack.leash, state.layer)
        }

        fun restoreLeash(taskStack: AutoTaskStack, transaction: Transaction) {
            if (taskStack !is RootTaskStack) {
                Slog.e(TAG, "Unsupported task stack, unable to restore leash")
                return
            }

            val rootTdaInfo = rootTdaOrganizer.getDisplayAreaInfo(taskStack.displayId)
            if (rootTdaInfo == null ||
                rootTdaInfo.featureId != taskStack.rootTaskInfo.displayAreaFeatureId
            ) {
                Slog.e(TAG, "Cannot find the rootTDA for the root task stack ${taskStack.id}")
                return
            }
            if (DBG) {
                Slog.d(TAG, "Reparenting ${taskStack.id} leash to DA ${rootTdaInfo.featureId}")
            }
            transaction.reparent(
                taskStack.leash,
                rootTdaOrganizer.getDisplayAreaLeash(taskStack.displayId)
            )
        }
    }

    inner class RootTaskStackListenerAdapter(
        val rootTaskStackListener: RootTaskStackListener,
    ) : ShellTaskOrganizer.TaskListener {
        private var rootTaskStack: RootTaskStack? = null

        // TODO(b/384948029): Notify car service for all the children tasks' events
        override fun onTaskAppeared(
            taskInfo: ActivityManager.RunningTaskInfo?,
            leash: SurfaceControl?
        ) {
            if (taskInfo == null) {
                throw IllegalArgumentException("taskInfo can't be null in onTaskAppeared")
            }
            if (leash == null) {
                throw IllegalArgumentException("leash can't be null in onTaskAppeared")
            }
            if (DBG) Slog.d(TAG, "onTaskAppeared = ${taskInfo.taskId}")

            if (rootTaskStack == null) {
                val rootTask =
                    RootTaskStack(taskInfo.taskId, taskInfo.displayId, leash, taskInfo)
                taskStackMap[rootTask.id] = rootTask

                rootTaskStack = rootTask
                rootTaskStackListener.onRootTaskStackCreated(rootTask)
                autoTaskRepository.onRootTaskStackCreated(rootTask)
                return
            }
            appTasksMap[taskInfo.taskId] = taskInfo
            rootTaskStackListener.onTaskAppeared(taskInfo, leash)
            autoTaskRepository.onTaskAppeared(rootTaskStack, taskInfo, leash)
        }

        override fun onTaskInfoChanged(taskInfo: ActivityManager.RunningTaskInfo?) {
            if (taskInfo == null) {
                throw IllegalArgumentException("taskInfo can't be null in onTaskInfoChanged")
            }
            if (DBG) Slog.d(TAG, "onTaskInfoChanged = ${taskInfo.taskId}")
            var previousRootTaskStackInfo = rootTaskStack ?: run {
                Slog.e(TAG, "Received onTaskInfoChanged, when root task stack is null")
                return@onTaskInfoChanged
            }
            rootTaskStack?.let {
                if (taskInfo.taskId == previousRootTaskStackInfo.id) {
                    previousRootTaskStackInfo =
                        previousRootTaskStackInfo.copy(rootTaskInfo = taskInfo)
                    taskStackMap[previousRootTaskStackInfo.id] = previousRootTaskStackInfo
                    rootTaskStack = previousRootTaskStackInfo
                    rootTaskStackListener.onRootTaskStackInfoChanged(it)
                    return
                }
            }

            appTasksMap[taskInfo.taskId] = taskInfo
            rootTaskStackListener.onTaskInfoChanged(taskInfo)
            autoTaskRepository.onTaskChanged(rootTaskStack, taskInfo)
        }

        override fun onTaskVanished(taskInfo: ActivityManager.RunningTaskInfo?) {
            if (taskInfo == null) {
                throw IllegalArgumentException("taskInfo can't be null in onTaskVanished")
            }
            if (DBG) Slog.d(TAG, "onTaskVanished  = ${taskInfo.taskId}")
            var rootTask = rootTaskStack ?: run {
                Slog.e(TAG, "Received onTaskVanished, when root task stack is null")
                return@onTaskVanished
            }
            if (taskInfo.taskId == rootTask.id) {
                rootTask = rootTask.copy(rootTaskInfo = taskInfo)
                rootTaskStack = rootTask
                rootTaskStackListener.onRootTaskStackDestroyed(rootTask)
                taskStackMap.remove(rootTask.id)
                taskStackStateMap.remove(rootTask.id)
                autoTaskRepository.onRootTaskStackDestroyed(rootTask)
                rootTaskStack = null
                return
            }
            appTasksMap.remove(taskInfo.taskId)
            rootTaskStackListener.onTaskVanished(taskInfo)
            autoTaskRepository.onTaskVanished(rootTaskStack, taskInfo)
        }

        override fun onBackPressedOnTaskRoot(taskInfo: ActivityManager.RunningTaskInfo?) {
            if (taskInfo == null) {
                throw IllegalArgumentException("taskInfo can't be null in onBackPressedOnTaskRoot")
            }
            super.onBackPressedOnTaskRoot(taskInfo)
            rootTaskStackListener.onBackPressedOnTaskRoot(taskInfo)
        }
    }

    override fun createRootTaskStack(
        displayId: Int,
        listener: RootTaskStackListener
    ) {
        taskOrganizer.createRootTask(
            displayId,
            WINDOWING_MODE_MULTI_WINDOW,
            RootTaskStackListenerAdapter(listener),
            /* removeWithTaskOrganizer= */
            true
        )
    }

    override fun destroyTaskStack(taskStackId: Int) {
        // TODO(b/384946072): Add support for DisplayAreaTaskStack
        val taskStack = taskStackMap[taskStackId] as? RootTaskStack
        if (taskStack == null) {
            Slog.e(TAG, "Task stack with id $taskStackId doesn't exist")
            return
        }
        val deleted: Boolean = taskOrganizer.deleteRootTask(taskStack.rootTaskInfo.token)
    }

    override fun setDefaultRootTaskStackOnDisplay(displayId: Int, rootTaskStackId: Int?) {
        var wct = WindowContainerTransaction()

        // Clear the default root task stack if already set
        defaultRootTaskPerDisplay[displayId]?.let { existingDefaultRootTaskStackId ->
            (taskStackMap[existingDefaultRootTaskStackId] as? RootTaskStack)?.let { rootTaskStack ->
                wct.setLaunchRoot(rootTaskStack.rootTaskInfo.token, null, null)
            }
        }

        if (rootTaskStackId != null) {
            var taskStack =
                taskStackMap[rootTaskStackId] ?: run { return@setDefaultRootTaskStackOnDisplay }
            if (DBG) Slog.d(TAG, "setting launch root for  = ${taskStack.id}")
            if (taskStack !is RootTaskStack) {
                throw IllegalArgumentException(
                    "Cannot set a non root task stack as default root task " +
                            "stack"
                )
            }
            wct.setLaunchRoot(
                taskStack.rootTaskInfo.token,
                intArrayOf(WINDOWING_MODE_UNDEFINED),
                intArrayOf(
                    ACTIVITY_TYPE_STANDARD,
                    ACTIVITY_TYPE_UNDEFINED,
                    ACTIVITY_TYPE_RECENTS,

                    // TODO(b/386242708): Figure out if this flag will ever be used for automotive
                    //  assistant. Based on output, remove it from here and fix the
                    //  AssistantStackTests accordingly.
                    ACTIVITY_TYPE_ASSISTANT
                )
            )
            defaultRootTaskPerDisplay[displayId] = taskStack.id
        }

        taskOrganizer.applyTransaction(wct)
    }

    override fun startTransition(transaction: AutoTaskStackTransaction): IBinder? {
        if (transaction.operations.isEmpty()) {
            Slog.e(TAG, "Operations empty, no transaction started")
            return null
        }
        if (DBG) Slog.d(TAG, "startTransaction ${transaction.operations}")

        var wct = WindowContainerTransaction()
        convertToWct(transaction, wct)
        var pending = PendingTransition(
            TRANSIT_CHANGE,
            wct,
            transaction,
        )
        return startTransitionNow(pending)
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? {
        if (DBG) {
            Slog.d(
                TAG,
                "handle request, id=${request.debugId}, type=${request.type}, " +
                        "triggertask = ${request.triggerTask ?: "null"}"
            )
        }
        val ast = autoTransitionHandlerDelegate?.handleRequest(transition, request)
            ?: run { return@handleRequest null }

        if (ast.operations.isEmpty()) {
            return null
        }
        var wct = WindowContainerTransaction()
        convertToWct(ast, wct)

        pendingTransitions.add(
            PendingTransition(request.type, wct, ast).apply { isClaimed = transition }
        )
        return wct
    }

    fun updateTaskStackStates(taskStatStates: Map<Int, AutoTaskStackState>) {
        taskStackStateMap.putAll(taskStatStates)
    }

    fun reconcileTaskStackStatesFromTransition(
        requestedTaskStackChanges: Map<Int, AutoTaskStackState>,
        changes: List<TransitionInfo.Change>
    ): Map<Int, AutoTaskStackState> {
        var changedTaskStacks = mutableMapOf<Int, AutoTaskStackState>()
        changedTaskStacks.putAll(requestedTaskStackChanges)

        for (chg in changes) {
            val taskInfo = chg.taskInfo ?: continue
            if (taskInfo.parentTaskId == INVALID_TASK_ID) continue
            if (taskStackMap[taskInfo.parentTaskId] == null) {
                if (DBG) {
                    Slog.v(
                        TAG,
                        "${taskInfo.taskId}'s parent ${taskInfo.parentTaskId} is not known"
                    )
                }
                continue
            }

            if (!TransitionUtil.isOpeningMode(chg.mode)) {
                if (DBG) Slog.v(TAG, "${taskInfo.taskId} is not opening type")
                continue
            }
            if (requestedTaskStackChanges[taskInfo.parentTaskId] != null &&
                requestedTaskStackChanges[taskInfo.parentTaskId]!!.childrenTasksVisible
            ) {
                if (DBG) {
                    Slog.v(
                        TAG,
                        "${taskInfo.taskId}'s parent ${taskInfo.parentTaskId} is already " +
                                "being changed to visible"
                    )
                }
                continue
            }
            if (DBG) {
                Slog.v(TAG, "${taskInfo.taskId} found conflicting task change")
            }
            val taskStackLayer = taskStackStateMap[taskInfo.taskId]?.layer ?: 1
            // Use a fixed layer 1 when state is unknown. This is just a placeholder and clients
            // should anyway see this as a conflict and fire a new transition with the correct layer
            changedTaskStacks[taskInfo.parentTaskId] = AutoTaskStackState(
                bounds = taskStackStateMap[taskInfo.taskId]?.bounds ?: Rect(),
                childrenTasksVisible = true,
                layer = taskStackLayer
            )
        }
        return changedTaskStacks
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: TransitionFinishCallback
    ): Boolean {
        if (DBG) Slog.d(TAG, "  startAnimation, id=${info.debugId} = changes=" + info.changes)
        val pending: PendingTransition? = findPending(transition)
        var changedTaskStacks = mutableMapOf<Int, AutoTaskStackState>()
        if (pending != null) {
            pendingTransitions.remove(pending)
            changedTaskStacks.putAll(
                reconcileTaskStackStatesFromTransition(
                    pending.transaction.getTaskStackStates(),
                    info.changes
                )
            )
            updateTaskStackStates(changedTaskStacks)
        }

        reorderLeashes(startTransaction)
        reorderLeashes(finishTransaction)

        for (chg in info.changes) {
            // TODO(b/384946072): handle the da stack similarly. The below implementation only
            // handles the root task stack

            val taskInfo = chg.taskInfo ?: continue
            val taskStack = taskStackMap[taskInfo.taskId] ?: continue

            // Restore the leashes for the task stacks to ensure correct z-order competition
            if (taskStackMap.containsKey(taskInfo.taskId)) {
                mTaskStackStateTranslator.restoreLeash(
                    taskStack,
                    startTransaction
                )
                if (TransitionUtil.isOpeningMode(chg.mode)) {
                    // Clients can still manipulate the alpha, but this ensures that the default
                    // behavior is natural
                    startTransaction.setAlpha(chg.leash, 1f)
                }
                continue
            }
        }

        val isPlayedByDelegate = autoTransitionHandlerDelegate?.startAnimation(
            transition,
            changedTaskStacks,
            info,
            startTransaction,
            finishTransaction,
            {
                shellMainThread.execute {
                    finishCallback.onTransitionFinished(it)
                    startNextTransition()
                }
            }
        ) ?: false

        if (isPlayedByDelegate) {
            if (DBG) Slog.d(TAG, "${info.debugId} played")
            return true
        }

        // If for an animation which is not played by the delegate, contains a change in a known
        // task stack, it should be leveraged to correct the leashes. So, handle the animation in
        // this case.
        if (info.changes.any { taskStackMap.containsKey(it.taskInfo?.taskId) }) {
            startTransaction.apply()
            finishCallback.onTransitionFinished(null)
            startNextTransition()
            if (DBG) Slog.d(TAG, "${info.debugId} played")
            return true
        }
        return false
    }

    fun convertToWct(ast: AutoTaskStackTransaction, wct: WindowContainerTransaction) {
        ast.operations.forEach { operation ->
            when (operation) {
                is TaskStackOperation.ReparentTask -> {
                    val appTask = appTasksMap[operation.taskId]

                    if (appTask == null) {
                        Slog.e(
                            TAG,
                            "task with id=$operation.taskId not found, failed to " +
                                    "reparent."
                        )
                        return@forEach
                    }
                    if (!taskStackMap.containsKey(operation.parentTaskStackId)) {
                        Slog.e(
                            TAG,
                            "task stack with id=${operation.parentTaskStackId} not " +
                                    "found, failed to reparent"
                        )
                        return@forEach
                    }
                    // TODO(b/384946072): Handle a display area stack as well
                    wct.reparent(
                        appTask.token,
                        (taskStackMap[operation.parentTaskStackId] as RootTaskStack)
                            .rootTaskInfo.token,
                        operation.onTop
                    )
                }

                is TaskStackOperation.SendPendingIntent -> wct.sendPendingIntent(
                    operation.sender,
                    operation.intent,
                    operation.options
                )

                is TaskStackOperation.SetTaskStackState -> {
                    taskStackMap[operation.taskStackId]?.let { taskStack ->
                        mTaskStackStateTranslator.applyVisibilityAndBounds(
                            wct,
                            taskStack,
                            operation.state
                        )
                    }
                        ?: Slog.w(
                            TAG, "AutoTaskStack with id ${operation.taskStackId} " +
                                    "not found."
                        )
                }
            }
        }
    }

    override fun mergeAnimation(
        transition: IBinder,
        info: TransitionInfo,
        surfaceTransaction: Transaction,
        mergeTarget: IBinder,
        finishCallback: TransitionFinishCallback
    ) {
        val pending: PendingTransition? = findPending(transition)

        autoTransitionHandlerDelegate?.mergeAnimation(
            transition,
            pending?.transaction?.getTaskStackStates() ?: mapOf(),
            info,
            surfaceTransaction,
            mergeTarget,
            /* finishCallback = */
            {
                shellMainThread.execute {
                    finishCallback.onTransitionFinished(it)
                }
            }
        )
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: Transaction?
    ) {
        val pending: PendingTransition? = findPending(transition)
        if (pending != null) {
            pendingTransitions.remove(pending)
            updateTaskStackStates(pending.transaction.getTaskStackStates())
            // Still update the surface order because this means wm didn't lead to any change
            if (finishTransaction != null) {
                reorderLeashes(finishTransaction)
            }
        }
        autoTransitionHandlerDelegate?.onTransitionConsumed(
            transition,
            pending?.transaction?.getTaskStackStates() ?: mapOf(),
            aborted,
            finishTransaction
        )
    }

    private fun reorderLeashes(transaction: SurfaceControl.Transaction) {
        taskStackStateMap.forEach { (taskId, taskStackState) ->
            taskStackMap[taskId]?.let { taskStack ->
                mTaskStackStateTranslator.reorderLeash(taskStack, taskStackState, transaction)
            } ?: Slog.w(TAG, "Warning: AutoTaskStack with id $taskId not found.")
        }
    }

    private fun findPending(claimed: IBinder) = pendingTransitions.find { it.isClaimed == claimed }

    private fun startTransitionNow(pending: PendingTransition): IBinder {
        val claimedTransition = transitions.startTransition(pending.mType, pending.wct, this)
        pending.isClaimed = claimedTransition
        pendingTransitions.add(pending)
        return claimedTransition
    }

    fun startNextTransition() {
        if (pendingTransitions.isEmpty()) return
        val pending: PendingTransition = pendingTransitions[0]
        if (pending.isClaimed != null) {
            // Wait for this to start animating.
            return
        }
        pending.isClaimed = transitions.startTransition(pending.mType, pending.wct, this)
    }

    internal class PendingTransition(
        @field:WindowManager.TransitionType @param:WindowManager.TransitionType val mType: Int,
        val wct: WindowContainerTransaction,
        val transaction: AutoTaskStackTransaction,
    ) {
        var isClaimed: IBinder? = null
    }
}
