package org.triplea.server.lib.scheduled.tasks;

/**
 * Framework-agnostic lifecycle contract for a managed background task. Implementations are started
 * on server startup and stopped on server shutdown.
 */
public interface TaskLifecycle {
  void start() throws Exception;

  void stop() throws Exception;
}
