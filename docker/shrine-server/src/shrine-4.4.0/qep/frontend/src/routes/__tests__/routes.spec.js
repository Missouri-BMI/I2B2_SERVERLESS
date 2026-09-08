import { Provider } from "react-redux";
import React from "react";
import { mount } from "enzyme";
import { MemoryRouter } from "react-router";
import { DndProvider } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import { auth, local, sessionStorage } from "utilities";
import { Routes } from "..";
import { getStore } from "../../getStore";
import { DataDistributionTypes } from "../../models/DataDistributionTypes";

const store = getStore();
const renderRoutes = (path) =>
  mount(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <DndProvider backend={HTML5Backend}>
          <Routes />
        </DndProvider>
      </MemoryRouter>
    </Provider>
  );

xdescribe("routes", () => {
  const authenticationResponseToken = {
    username: "adminSession",
    sessionId: "1234567",
  };
  const sessionStore = {};
  const mockStorage = {
    setItem: (key, data) => {
      sessionStore[key] = data;
    },
    getItem: (key) => sessionStore[key],
    removeItem: (key) => {
      delete sessionStore[key];
    },
  };
  auth.storage = mockStorage;
  local.localKey = "mock session key";
  sessionStorage.sessionKey = "mock session key";

  it("Directs an unauthenticated user to the Login page.", () => {
    console.error = jest.fn(); // silence unrelated useEffect warning.
    const component = renderRoutes("/");
    expect(component.find(".Login").length).toBe(1);
    expect(component.find(".Researcher").length).toBe(0);
  });
  it("Direct an authenticated user to the Researcher page.", () => {
    console.error = jest.fn(); // silence unrelated useEffect warning.
    auth.token = authenticationResponseToken;
    store.dispatch({
      type: "LOGIN_USER_SUCCEEDED",
      payload: {
        sessionTimeoutMs: 1800000,
      },
    });
    const component = renderRoutes("/");
    expect(component.find(".Login").length).toBe(0);
    expect(component.find(".Researcher").length).toBe(1);
  });
});
