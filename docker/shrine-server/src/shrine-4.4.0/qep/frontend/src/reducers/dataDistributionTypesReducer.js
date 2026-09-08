import {
  FETCH_DATA_DISTRIBUTION_TYPES,
  FETCH_DATA_DISTRIBUTION_TYPES_SUCCEEDED,
  FETCH_DATA_DISTRIBUTION_TYPES_FAILED,
} from "actions";
import { defaultState } from "defaultState";
import { DataDistributionTypes, Error } from "models";

export const dataDistributionTypesReducer = (
  state = defaultState.dataDistributionTypes,
  action
) => {
  switch (action.type) {
    case FETCH_DATA_DISTRIBUTION_TYPES: {
      return DataDistributionTypes({
        ...state,
        isFetching: true,
        isLoaded: false,
      });
    }
    case FETCH_DATA_DISTRIBUTION_TYPES_SUCCEEDED: {
      let dataDistTypes = {...action.payload};
      return DataDistributionTypes({
        allOutputTypes: dataDistTypes,
        isFetching: false,
        isLoaded: true,
      });
    }
    case FETCH_DATA_DISTRIBUTION_TYPES_FAILED: {
      const { status, statusText, url } = action.payload;
      const error = Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      });

      return DataDistributionTypes({
        ...state,
        isFetching: false,
        isLoaded: false,
        error
      });
    }
    default:
      return DataDistributionTypes(state);
  }
};
