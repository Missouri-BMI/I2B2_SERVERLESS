import { clearOnLogout } from "../clearOnLogout";

describe("clearOnLogout ", () => {
  const logoutState = {
    mockProp: "default state value"
  };
  const state = {
    mockProp: "current state value"
  };
  it("should provide the default state if the actionType is CLEAR_LOGIN", () => {
    expect(clearOnLogout("CLEAR_LOGIN", logoutState, state)).toEqual(
      logoutState
    );
  });

  it("should provide the current state if the actionType is not CLEAR_LOGIN", () => {
    expect(clearOnLogout("SOME_OTHER_ACTION", logoutState, state)).toEqual(
      state
    );
  });
});
