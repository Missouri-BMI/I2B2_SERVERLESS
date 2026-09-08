import React, { useState } from "react";

import "./MainContent.scss";
import { LeftContentToggleBtn } from "./LeftContentToggleBtn";

export const MainContent = ({ LeftContent, RightContent, view }) => {
  const [open, setOpen] = useState(true);
  return (
    <div className="row mb-2 MainContent">
      <div className="flex-container">
        <div className={open ? "left-content open" : "left-content closed"}>
          {<LeftContent view={view} />}
        </div>
        <div className="right-content">
          <LeftContentToggleBtn open={open} setOpen={setOpen} />
          <RightContent view={view} />
        </div>
      </div>
    </div>
  );
};
