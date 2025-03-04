package gov.nasa.jpl.aerie.scheduler.worker.services;

import java.util.ArrayList;
import gov.nasa.jpl.aerie.scheduler.server.ResultsProtocol;
import gov.nasa.jpl.aerie.scheduler.server.services.ScheduleFailure;
import gov.nasa.jpl.aerie.scheduler.server.services.ScheduleResults;

class MockResultsProtocolWriter implements ResultsProtocol.WriterRole {
  final ArrayList<Result> results;

  MockResultsProtocolWriter() {
    this.results = new ArrayList<>();
  }

  sealed interface Result {
    record Success(ScheduleResults results) implements Result {}

    record Failure(ScheduleFailure reason) implements Result {}
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public void succeedWith(final ScheduleResults results) {
    this.results.add(new Result.Success(results));
  }

  @Override
  public void failWith(final ScheduleFailure reason) {
    this.results.add(new Result.Failure(reason));
  }
}
