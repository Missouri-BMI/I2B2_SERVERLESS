import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import "./DropDown.scss";

export default function DropDown({ options }) {
  const [selectedOption, setSelectionOption] = useState([]);
  const [displayKey, displayName] = selectedOption;

  useEffect(() => {
    setSelectionOption([...options][0]);
  }, []);

  return (
    <>
      <div className="dropdown DropDown">
        <div
          className="dropdown-toggle"
          role="button"
          data-toggle="dropdown"
          aria-haspopup="true"
          aria-expanded="false"
        >
          <div className="display-name">{displayName}</div>
          <i className="fa fa-chevron-down" aria-hidden="true" />
        </div>

        <div
          className="dropdown-menu dropdown-content"
          aria-labelledby="dropdownMenuLink"
        >
          {[...options].map(option => {
            const [key, value] = option;
            return (
              <div
                className={
                  key === displayKey
                    ? " dropdown-item selected"
                    : "dropdown-item"
                }
                role="button"
                key={key}
                onClick={() => setSelectionOption([key, value])}
              >
                {value}
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
}

// TODO: this will be replaced with real data.
DropDown.defaultProps = {
  options: new Map([
    ["All Concepts", "All Concepts"],
    ["ACT Demographics", "ACT Demographics"],
    ["ACT Diagnosis ICD-10", "ACT Diagnosis ICD-10"],
    ["ACT Diagnosis ICD10-ICD9", "ACT Diagnosis ICD10-ICD9"],
    ["ACT Laboratory Tests", "ACT Laboratory Tests"],
    ["ACT Procedures ICD-9-Proc", "ACT Procedures ICD-9-Proc"]
  ])
};

DropDown.proTypes = {
  options: PropTypes.instanceOf(Map)
};
