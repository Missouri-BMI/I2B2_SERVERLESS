import { FETCH_FILTER_OPTIONS, FETCH_FILTER_OPTIONS_SUCCEEDED } from "actions";

export const filterOptionsReducer = (state, action) => {
  switch (action.type) {
    case FETCH_FILTER_OPTIONS_SUCCEEDED: {
      return {
        ...state,
        filterOptions: action.payload
      };
    }
    case FETCH_FILTER_OPTIONS:
    default:
      return state;
  }
};
