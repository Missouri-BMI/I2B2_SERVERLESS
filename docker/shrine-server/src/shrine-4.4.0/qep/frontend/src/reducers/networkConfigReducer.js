import {
  FETCH_NETWORK_CONFIG,
  FETCH_NETWORK_CONFIG_SUCCEEDED,
  FETCH_NETWORK_CONFIG_FAILED
} from "actions";
import { defaultState } from "defaultState";
import { NetworkConfig, Error } from "models";

export const networkConfigReducer = (
  state = defaultState.networkConfig,
  action
) => {
  switch (action.type) {
    case FETCH_NETWORK_CONFIG: {
      return NetworkConfig({
        ...state,
        isFetching: true
      });
    }
    case FETCH_NETWORK_CONFIG_SUCCEEDED: {
      return NetworkConfig({
        ...action.payload,
        isFetching: false
      });
    }
    case FETCH_NETWORK_CONFIG_FAILED: {
      const { status, statusText, url } = action.payload;
      const error = Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      });

      return NetworkConfig({
        ...state,
        isFetching: false,
        error
      });
    }
    default:
      return NetworkConfig(state);
  }
};
