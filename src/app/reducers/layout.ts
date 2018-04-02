/**
 * Copyright 2018, by the California Institute of Technology. ALL RIGHTS RESERVED. United States Government Sponsorship acknowledged.
 * Any commercial use must be negotiated with the Office of Technology Transfer at the California Institute of Technology.
 * This software may be subject to U.S. export control laws and regulations.
 * By accepting this document, the user agrees to comply with all applicable U.S. export laws and regulations.
 * User has the responsibility to obtain export licenses, or other export authority as may be required
 * before exporting such information to foreign countries or providing access to foreign persons
 */

import { createFeatureSelector, createSelector } from '@ngrx/store';

import {
  LayoutAction,
  LayoutActionTypes,
  SetMode,
} from './../actions/layout';

// Layout State Interface.
export interface LayoutState {
  mode: string;
  showDataPointDrawer: boolean;
  showDetailsDrawer: boolean;
  showLeftDrawer: boolean;
  showSouthBandsDrawer: boolean;
  timelinePanelSize: number;
}

// Layout State.
export const initialState: LayoutState = {
  mode: 'default',
  showDataPointDrawer: false,
  showDetailsDrawer: true,
  showLeftDrawer: true,
  showSouthBandsDrawer: true,
  timelinePanelSize: 75,
};

/**
 * Reducer.
 * If a case takes more than one line then it should be in it's own helper function.
 */
export function reducer(state: LayoutState = initialState, action: LayoutAction): LayoutState {
  switch (action.type) {
    case LayoutActionTypes.SetMode:
      return setMode(state, action);
    case LayoutActionTypes.ToggleDetailsDrawer:
      return { ...state, showDetailsDrawer: !state.showDetailsDrawer };
    case LayoutActionTypes.ToggleLeftDrawer:
      return { ...state, showLeftDrawer: !state.showLeftDrawer };
    case LayoutActionTypes.OpenDataPointDrawer:
      return { ...state, showDataPointDrawer: true };
    case LayoutActionTypes.CloseDataPointDrawer:
      return { ...state, showDataPointDrawer: false };
    case LayoutActionTypes.SetTimelinePanelSize:
      return { ...state, timelinePanelSize: action.panelSize };
    case LayoutActionTypes.ToggleSouthBandsDrawer:
      return { ...state, showSouthBandsDrawer: !state.showSouthBandsDrawer };
    default:
      return state;
  }
}

/**
 * Reduction Helper. Called when reducing the 'SetMode' action.
 */
export function setMode(state: LayoutState, action: SetMode): LayoutState {
  return {
    ...state,
    mode: action.mode,
    showDetailsDrawer: action.showDetailsDrawer,
    showLeftDrawer: action.showLeftDrawer,
    showSouthBandsDrawer: action.showSouthBandsDrawer,
  };
}

/**
 * Layout state selector helper.
 */
export const getLayoutState = createFeatureSelector<LayoutState>('layout');

/**
 * Create selector helper for selecting state slice.
 *
 * Every reducer module exports selector functions, however child reducers
 * have no knowledge of the overall state tree. To make them usable, we
 * need to make new selectors that wrap them.
 *
 * The createSelector function creates very efficient selectors that are memoized and
 * only recompute when arguments change. The created selectors can also be composed
 * together to select different pieces of state.
 */
export const getShowDrawers = createSelector(getLayoutState, (state: LayoutState) => ({
  showDataPointDrawer: state.showDataPointDrawer,
  showDetailsDrawer: state.showDetailsDrawer,
  showLeftDrawer: state.showLeftDrawer,
  showSouthBandsDrawer: state.showSouthBandsDrawer,
  timelinePanelSize: state.timelinePanelSize,
}));
