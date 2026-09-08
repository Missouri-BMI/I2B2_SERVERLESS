import { CLEAR_LOGIN } from "actions";

export const clearOnLogout = (actionType, logoutState, state) =>
  actionType === CLEAR_LOGIN ? logoutState : state;
