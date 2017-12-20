import { Action } from '@ngrx/store';

export enum LayoutActionTypes {
  ToggleLeftDrawer = 'toggle_left_drawer',
}

export class ToggleLeftDrawer implements Action {
  readonly type = LayoutActionTypes.ToggleLeftDrawer;
}

export type LayoutActions = ToggleLeftDrawer;
