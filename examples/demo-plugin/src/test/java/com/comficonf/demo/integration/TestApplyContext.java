package com.comficonf.demo.integration;

import com.comficonf.api.Actor;
import com.comficonf.api.ApplyContext;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.FieldChange;
import com.comficonf.api.ResourceId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link ApplyContext} for provider tests; {@code callSync} runs the task on the calling thread. */
final class TestApplyContext implements ApplyContext {

    final AtomicInteger syncCalls = new AtomicInteger();
    private final String baseRevision;
    private final ConfigValues current;
    private final ConfigValues proposed;

    TestApplyContext(String baseRevision, ConfigValues current, ConfigValues proposed) {
        this.baseRevision = baseRevision;
        this.current = current;
        this.proposed = proposed;
    }

    @Override
    public ResourceId resourceId() {
        return ResourceId.parse(ComfiConfIntegration.RESOURCE_ID);
    }

    @Override
    public String operationId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String baseRevision() {
        return baseRevision;
    }

    @Override
    public ConfigValues current() {
        return current;
    }

    @Override
    public ConfigValues proposed() {
        return proposed;
    }

    @Override
    public List<FieldChange> changes() {
        return List.of();
    }

    @Override
    public boolean requiresRestart() {
        return false;
    }

    @Override
    public Actor actor() {
        return new Actor(UUID.randomUUID().toString(), "owner@example.com");
    }

    @Override
    public <T> T callSync(Callable<T> task) throws Exception {
        syncCalls.incrementAndGet();
        return task.call();
    }
}
