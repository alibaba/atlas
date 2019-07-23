package com.taobao.android.builder.tasks.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkipUpdateToDateExecuter
 *
 * @author zhayu.ll
 * @date 18/3/1
 */
public class SkipExecuter implements TaskExecuter{

    private static final Logger LOGGER = LoggerFactory.getLogger(SkipExecuter.class);

    private TaskExecuter executer;

    public SkipExecuter(TaskExecuter executer) {
        this.executer = executer;
    }
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.info("skip SkipExecuter");
        return executer.execute(task, state, context);
    }
}
