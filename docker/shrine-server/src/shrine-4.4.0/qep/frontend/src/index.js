import "./index.scss";

import "typeface-roboto";

import "@fortawesome/fontawesome-free/css/v4-shims.css";
import "@fortawesome/fontawesome-free/css/fontawesome.css";
import "@fortawesome/fontawesome-free/css/regular.css";
import "@fortawesome/fontawesome-free/css/solid.css";

import React from "react";
import { render } from "react-dom";
import { AppContainer } from "react-hot-loader";

import App from "./App";

render(<App />, document.getElementById("app"));

if (module.hot) {
  module.hot.accept("./App", () => {
    const NextApp = require("./App").default;
    render(
      <AppContainer>
        <NextApp />
      </AppContainer>,
      document.getElementById("app")
    );
  });
}
