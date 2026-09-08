import React from "react";
import "./FetchError.scss";

const FetchError = ({ error }) => (
  <div className="FetchError">
    <div>Error connecting to: {` ${error.url}`}</div>
    <div>{error.message}</div>
  </div>
);

export default FetchError;
