package com.taobao.android.builder.tasks.execution;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        LOGGER.info("skip SkipExecuter");
        executer.execute(task, state, context);
    }
}
