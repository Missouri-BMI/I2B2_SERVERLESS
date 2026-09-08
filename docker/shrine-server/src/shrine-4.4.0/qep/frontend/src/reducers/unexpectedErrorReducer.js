import { defaultState } from "defaultState";
import { Error } from "models";
import { UNEXPECTED_ERROR } from "actions";

export const unexpectedErrorReducer = (
  state = defaultState.unexpectedError,
  action
) => {
  switch (action.type) {
    case UNEXPECTED_ERROR: {
      const { status, statusText, url } = action.payload;
      return Error({
        hasError: true,
        message: `${status} ${statusText}`,
        url
      });
    }
    default: {
      return Error(state);
    }
  }
};
