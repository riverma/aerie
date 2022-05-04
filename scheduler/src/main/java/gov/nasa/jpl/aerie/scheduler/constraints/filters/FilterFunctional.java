package gov.nasa.jpl.aerie.scheduler.constraints.filters;

import gov.nasa.jpl.aerie.constraints.model.SimulationResults;
import gov.nasa.jpl.aerie.constraints.time.Window;
import gov.nasa.jpl.aerie.constraints.time.Windows;
import gov.nasa.jpl.aerie.scheduler.model.Plan;

import java.util.ArrayList;
import java.util.List;

public abstract class FilterFunctional implements TimeWindowsFilter {


  @Override
  public Windows filter(SimulationResults simulationResults, Plan plan, Windows windows) {
    List<Window> ret = new ArrayList<>();
    for (var window : windows) {
      if (shouldKeep(simulationResults, plan, window)) {
        ret.add(window);
      }
    }
    return new Windows(ret);
  }


  public abstract boolean shouldKeep(SimulationResults simulationResults, Plan plan, Window range);
}
