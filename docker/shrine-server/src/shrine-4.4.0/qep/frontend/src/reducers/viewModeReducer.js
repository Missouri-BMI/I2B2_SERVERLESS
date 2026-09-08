import { ViewModeTypes, ViewMode, isValidViewMode } from "models";
import { defaultState } from "defaultState";
import { UPDATE_VIEW_MODE, LOGIN_USER_SUCCEEDED, CLEAR_LOGIN } from "actions";
import { sessionStorage } from "utilities";

export const viewModeReducer = (state = defaultState.viewMode, action) => {
  switch (action.type) {
    case LOGIN_USER_SUCCEEDED: {
      return ViewMode({
        type: ViewModeTypes.QUERY_DEFINITION,
        justLoggedIn: true
      });
    }
    case CLEAR_LOGIN: {
      return ViewMode({
        type: ViewModeTypes.LOGIN
      });
    }
    case UPDATE_VIEW_MODE: {
      const { payload: type } = action;
      if (isValidViewMode(type)) {
        return ViewMode({ type });
      }
      return state;
    }
    default: {
      if (sessionStorage.hasUserConfig()) {
        const defaultViewModeType = ViewModeTypes.QUERY_DEFINITION;
        const {
          viewMode: cachedViewModeType = defaultViewModeType
        } = sessionStorage.getUserConfig();

        return ViewMode({
          ...state,
          type: cachedViewModeType
        });
      }
      return ViewMode(state);
    }
  }
};
