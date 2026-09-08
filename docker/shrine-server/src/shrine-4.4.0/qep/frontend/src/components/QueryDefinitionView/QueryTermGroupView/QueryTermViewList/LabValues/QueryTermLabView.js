import React, { useEffect, useState } from "react";
import PropTypes from "prop-types";
import { Divider, MenuItem } from "@material-ui/core";

import "./QueryTermLabView.scss";
import { secureFetch, getBaseUrl, validateLabValues } from "utilities";
import { QueryTermView } from "../QueryTermView";
import UnitSelect from "./UnitSelect";
import ValueRange from "./ValueRange";
import TypeSelect from "./TypeSelect";
import { numberTypes, rangeTypes, QueryTerm } from "models";

/**
 * Based on a lab detail object, create a corresponding list of menu items of
 * possible ways to filter the term.
 */
function makeLabValueDropDownList(labDetail) {
  const toTitleCase = (str) => {
    return str
      .split(" ")
      .map((e) => e.charAt(0).toUpperCase() + e.substr(1).toLowerCase())
      .join(" ");
  };

  const anyOption = [<MenuItem value="ANY">Any Value</MenuItem>];
  const flagOptions =
    labDetail.flagValues &&
    labDetail.flagValues.map((flagName) => (
      <MenuItem value={flagName.toUpperCase()}>
        Abnormal Flag: {flagName}
      </MenuItem>
    ));
  const valueOptions = labDetail.units && [
    <MenuItem value="LT">Less Than ({"<"})</MenuItem>,
    <MenuItem value="LE">Less Than or Equal To ({"<="})</MenuItem>,
    <MenuItem value="EQ">Equal To (=)</MenuItem>,
    <MenuItem value="BETWEEN">Between</MenuItem>,
    <MenuItem value="GT">Greater Than ({">"})</MenuItem>,
    <MenuItem value="GE">Greater Than or Equal To ({">="})</MenuItem>,
  ];
  /** Enum values are not currently supported so we do not include them in the list. */
  const enumOptions =
    labDetail.enumValues &&
    labDetail.enumValues.map((enumValue) => (
      <MenuItem value={enumValue}>{toTitleCase(enumValue)}</MenuItem>
    ));

  const includedOptions = [flagOptions, valueOptions].filter((el) => !!el);
  const optionList = anyOption.concat(
    ...includedOptions.reduce((acc, e) => acc.concat(<Divider />, e), [])
  );
  return optionList;
}

export default function QueryTermLabView({
  term,
  onConstraintChange,
  onDeleteTermClicked,
  fetchConceptInfo,
  ...otherProps
}) {
  const [labDetail, setLabDetail] = useState(null);
  const [labDetailOptionList, setLabDetailOptionList] = useState([]);
  const { constraintType, value, unit } = term.constraint;
  const [startValue, endValue] = value;
  const [
    lowValueError,
    lowValueHelperText,
    highValueError,
    highValueHelperText,
  ] = validateLabValues(constraintType, startValue, endValue, unit);

  const fetchLabDetail = async (path) => {
    const url = `${getBaseUrl()}ontology/labDetails`;
    const headers = {
      "Content-Type": "application/json",
    };
    const fetchConfig = {
      headers,
      method: "POST",
      body: JSON.stringify({ path }),
    };

    const result = await secureFetch(url, fetchConfig);
    if (result.ok) {
      setLabDetail(result.data);
    }
  };

  const getInputChangeHandler = (setter) => (event) => {
    const numberInputTest = /^[0-9]\d{0,9}(\.\d{0,9})?%?$/g;
    const val = event.target.value.slice(0, 10);
    if (!val.length) {
      setter(null);
    } else if (numberInputTest.test(val)) {
      setter(parseFloat(val));
    }
  };

  const hasDefaultUnit = () =>
    labDetail && labDetail.units && labDetail.units.length;
  const handleTypeChange = (event) =>
    onConstraintChange({
      constraintType: event.target.value,
      value: [],
      unit: hasDefaultUnit() ? labDetail.units[0] : "",
    });
  const handleUnitChange = (event) =>
    onConstraintChange({ unit: event.target.value });
  const handleStartValueChange = (val) =>
    onConstraintChange({ value: [val, endValue] });
  const handleEndValueChange = (val) =>
    onConstraintChange({ value: [startValue, val] });

  const getSpecifiedValuesClass = () => {
    if (numberTypes.includes(constraintType)) return "show-unit";
    if (rangeTypes.includes(constraintType)) return "show-range";
    return "";
  };

  useEffect(() => {
    fetchLabDetail(term.path);
  }, []);

  useEffect(() => {
    if (labDetail) {
      setLabDetailOptionList(makeLabValueDropDownList(labDetail));
    }
  }, [labDetail]);

  return (
    <QueryTermView
      term={term}
      onDeleteTermClicked={onDeleteTermClicked}
      fetchConceptInfo={fetchConceptInfo}
      {...otherProps}
    >
      {labDetail && labDetailOptionList.length > 1 && (
        <div className="QueryTermLabView">
          <TypeSelect
            labDetailOptionList={labDetailOptionList}
            type={constraintType}
            visibilityClass={getSpecifiedValuesClass()}
            onTypeChange={handleTypeChange}
          />
          <ValueRange
            startValue={startValue}
            endValue={endValue}
            startLabel={lowValueHelperText}
            endLabel={highValueHelperText}
            isStartError={lowValueError}
            isEndError={highValueError}
            visibilityClass={getSpecifiedValuesClass()}
            onStartChange={getInputChangeHandler(handleStartValueChange)}
            onEndChange={getInputChangeHandler(handleEndValueChange)}
          />
          <UnitSelect
            visibilityClass={getSpecifiedValuesClass()}
            units={labDetail.units}
            value={unit}
            onChange={handleUnitChange}
          />
        </div>
      )}
    </QueryTermView>
  );
}

QueryTermLabView.propTypes = {
  term: PropTypes.shape(QueryTerm.propTypes).isRequired,
  onConstraintChange: PropTypes.func.isRequired,
  onDeleteTermClicked: PropTypes.func.isRequired,
};
