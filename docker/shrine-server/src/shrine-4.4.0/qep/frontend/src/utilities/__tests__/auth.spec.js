import auth, { SHRINE_AUTHENTICATION_TOKEN_ID } from "../auth";

describe("authorization utlility service (auth.js)", () => {
  const authenticationResponseToken = {
    username: "adminSession",
    sessionId: "1234567"
  };
  const store = {};
  const mockStorage = {
    setItem: (key, data) => {
      store[key] = data;
    },
    getItem: (key) => store[key],
    removeItem: (key) => {
      delete store[key];
    }
  };
  auth.storage = mockStorage;

  it("Should save an item to storage", () => {
    auth.token = authenticationResponseToken;
    expect(store[SHRINE_AUTHENTICATION_TOKEN_ID]).toBe(
      btoa(JSON.stringify(authenticationResponseToken))
    );
  });

  it("Should retrieve the token authorization object.", () => {
    expect(auth.tokenAuthorizationObject).toEqual(authenticationResponseToken);
  });

  it("Should provide an Authorization header", () => {
    const { Authorization } = auth;
    expect(Authorization).toBe(
      `Bearer ${btoa(JSON.stringify(authenticationResponseToken))}`
    );
  });

  it("Should not allow the token to be changed", () => {
    const exceptionThrower = () => {
      auth.token = "changed mock token";
    };
    expect(exceptionThrower).toThrow(
      "Session authorization token cannot be changed"
    );
  });

  it("Should create a secure fetch config with a Bearer Authorization header containing the token", () => {
    const clientConfig = {
      type: "POST",
      body: JSON.stringify("mock post body"),
      headers: {
        "Content-Type": "application/json",
        Authorization: "Mock Authorization",
        "X-Requested-With": "XMLHttpRequest"
      }
    };

    const expected = {
      type: "POST",
      body: JSON.stringify("mock post body"),
      headers: {
        "Content-Type": "application/json",
        "X-Requested-With": "XMLHttpRequest",
        Authorization: `Bearer ${btoa(
          JSON.stringify(authenticationResponseToken)
        )}`
      }
    };

    const actual = auth.getFetchConfig(clientConfig);

    expect(actual).toEqual(expected);
  });

  it("Should remove an item from storage", () => {
    expect(store[SHRINE_AUTHENTICATION_TOKEN_ID]).toBe(
      btoa(JSON.stringify(authenticationResponseToken))
    );
    auth.clear();
    expect(store[SHRINE_AUTHENTICATION_TOKEN_ID]).toBeUndefined();
  });
});
