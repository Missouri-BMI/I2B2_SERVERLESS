import React, { useState } from "react";

import "./Search.scss";

export default function Search() {
  const [text, setText] = useState("");

  const updateText = event => {
    setText(event.target.value);
  };

  const reset = () => {
    setText("");
  };

  const handleSubmit = event => {
    event.preventDefault();
  };

  return (
    <>
      <form className="Search" onSubmit={handleSubmit}>
        <i className="fa fa-search" />
        <input
          type="text"
          placeholder="search"
          onChange={updateText}
          value={text}
        />
        <span className="clear" onMouseDown={reset} role="button">
          <i className="fa fa-times-circle" />
        </span>
        <input type="submit" />
      </form>
    </>
  );
}
