import { local, sessionStorage } from "utilities";

describe("session utility service (local.js)", () => {
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

  local.storage = mockStorage;

  it("should throw an error if the token has not been set when trying to access it.", () => {
    expect(() => local.localKey).toThrow(
      Error("You are trying to access a null session key.")
    );
  });

  it("should access the provided local key", () => {
    local.localKey = "mock session key";
    expect(local.localKey).toEqual("mock session key");
  });

  it("hasUserConfig should return false if the config has not been set", () => {
    expect(local.hasUserConfig()).toBe(false);
  });

  it("hasUserConfig should be true if the config has been set", () => {
    const data = {
      mockProperty: "mock propertly value"
    };
    local.setUserConfig(data);
    expect(local.hasUserConfig()).toBe(true);
    expect(JSON.stringify(local.getUserConfig())).toEqual(JSON.stringify(data));
  });
});


describe("session utility service (sessionStorage.js)", () => {
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

  sessionStorage.storage = mockStorage;

  it("should throw an error if the token has not been set when trying to access it.", () => {
    expect(() => sessionStorage.sessionKey).toThrow(
      Error("You are trying to access a null session key.")
    );
  });

  it("should access the provided session key", () => {
    sessionStorage.sessionKey = "mock session key";
    expect(sessionStorage.sessionKey).toEqual("mock session key");
  });

  it("hasUserConfig should return false if the local config has not been set", () => {
    expect(sessionStorage.hasUserConfig()).toBe(false);
  });

  it("hasUserConfig should be true if the local config has been set", () => {
    const data = {
      mockProperty: "mock property value"
    };
    sessionStorage.setUserConfig(data);
    expect(sessionStorage.hasUserConfig()).toBe(true);
    expect(JSON.stringify(sessionStorage.getUserConfig())).toEqual(JSON.stringify(data));
  });
});
