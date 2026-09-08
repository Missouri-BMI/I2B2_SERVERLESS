import React from "react";
import { Provider } from "react-redux";
import { HashRouter } from "react-router-dom";
import { DndProvider } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import { getStore } from "getStore";
import { Header, Footer, UnexpectedError } from "components";
import { Routes } from "routes";

const store = getStore();

export default () => {
  return (
    <Provider store={store}>
      <Header />
      <UnexpectedError />
      <HashRouter>
        <DndProvider backend={HTML5Backend}>
          <Routes />
        </DndProvider>
      </HashRouter>
      <Footer />
      <div />
    </Provider>
  );
};
