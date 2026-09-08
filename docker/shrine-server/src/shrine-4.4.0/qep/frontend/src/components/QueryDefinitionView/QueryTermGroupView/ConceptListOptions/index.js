import React, { useState, useEffect, useContext } from "react";
import PropTypes from "prop-types";

import "./ConceptListOptions.scss";

import { getDatetime, validateDates, minAcceptableDate } from "utilities";
import { Accordion, QueryDefinitionContext } from "components";
import { QueryTermGroupOptions } from "models";
import DateRange from "./DateRange";
import Occurrences from "./Occurrences";
import { DisableRangeOccurrenceHelpTooltip } from "./DisableRangeOccurenceHelpTooltip";

export const ConceptListOptions = ({
  queryTermGroupOptions,
  onChange,
  containsDemographic,
  includeOccurrences,
}) => {
  const { renderGroupAsExpanded } = useContext(QueryDefinitionContext);
  const [initialExpand, setInitialExpand] = useState(false);
  const [startDateError, setStartDateError] = useState(false);
  const [startDateLabel, setStartDateLabel] = useState("");
  const [endDateError, setEndDateError] = useState(false);
  const [endDateLabel, setEndDateLabel] = useState("");
  const hasDateRange = queryTermGroupOptions.startDate || queryTermGroupOptions.endDate;
  const hasOccurrences = includeOccurrences && queryTermGroupOptions.occurrences > 1;
  const invalidOccurrences = Number.isNaN(queryTermGroupOptions.occurrences) || queryTermGroupOptions.occurrences === 0;
  const multilineClass = hasDateRange && hasOccurrences ? "multiline" : "";
  const disabledClass = containsDemographic ? "disabled" : "";
  const clearSettingsError =
    containsDemographic && (hasDateRange || hasOccurrences);
  const notExpandable = containsDemographic && !clearSettingsError;
  const notExpandableClass = notExpandable ? "not-expandable" : "";
  const clearSettingsErrorClass = clearSettingsError
    ? "clear-settings-error"
    : "";

  const getDateRangeText = () => {
    if (queryTermGroupOptions.startDate && !queryTermGroupOptions.endDate) {
      return `Concepts must occur starting from ${getDatetime(queryTermGroupOptions.startDate)}`;
    }

    if (queryTermGroupOptions.endDate && !queryTermGroupOptions.startDate) {
      return `Concepts must occur on or before ${getDatetime(queryTermGroupOptions.endDate)}`;
    }

    return `Concepts must occur from ${getDatetime(queryTermGroupOptions.startDate)} to ${getDatetime(
      queryTermGroupOptions.endDate
    )}`;
  };

  const getOccurrencesText = () =>
    `Concepts must occur at least ${queryTermGroupOptions.occurrences} ${
      queryTermGroupOptions.occurrences > 1 ? "times" : "time"
    }`;

  const getOptionsDescription = () => {
    const defaultText = includeOccurrences
      ? "set date range or multiple occurrences"
      : "set date range";
    if (renderGroupAsExpanded) return defaultText;
    if (hasDateRange && hasOccurrences) {
      return `${getDateRangeText()} and ${getOccurrencesText().toLowerCase()}.`;
    }
    if (hasDateRange) {
      return `${getDateRangeText()}.`;
    }
    if (hasOccurrences) {
      return `${getOccurrencesText()}.`;
    }
    return defaultText;
  };

  const resetOccurrences = () => {
    handleChange(queryTermGroupOptions.startDate, queryTermGroupOptions.endDate, 1);
  };

  const resetDateRange = () => {
    onChange(null, null, queryTermGroupOptions.occurrences);
  };

  const handleOccurrencesChange = (event) => {
    const { value } = event.target;
    handleChange(queryTermGroupOptions.startDate, queryTermGroupOptions.endDate, parseInt(value.replace(/\D/, "").slice(0, 3), 10))
  };

  const handleChange = (updatedStartDate, updatedEndDate, updatedOccurrence) => {
    const newStartDate =
      updatedStartDate && !Number.isNaN(updatedStartDate.valueOf())
        ? updatedStartDate.valueOf()
        : updatedStartDate;
    const newEndDate =
      updatedEndDate && !Number.isNaN(updatedEndDate.valueOf()) ? updatedEndDate.valueOf() : updatedEndDate;
    onChange(newStartDate, newEndDate, updatedOccurrence);
  };

  const validateAndUpdateDate = (startDate=queryTermGroupOptions.startDate, endDate=queryTermGroupOptions.endDate, occurrences = queryTermGroupOptions.occurrences) => {
    setStartDateError(false);
    setEndDateError(false);
    setStartDateLabel("");
    setEndDateLabel("");

    const { validStartDate, validEndDate, validDateRange } = validateDates(
      startDate,
      endDate
    );

    if (containsDemographic) {
      if (startDate) {
        setStartDateError(true);
        setStartDateLabel("reset");
      }
      if (endDate) {
        setEndDateError(true);
        setEndDateLabel("reset");
      }
      startDate = null;
      endDate = null;
      occurrences = 1;
    } else if (hasDateRange) {
      const newStartDateError = startDate && !validStartDate;
      const newEndDateError = endDate && !validEndDate;
      setStartDateError(newStartDateError);
      setStartDateLabel(newStartDateError ? "invalid date" : "");
      setEndDateError(newEndDateError);
      setEndDateLabel(newEndDateError ? "invalid date" : "");

      if (validStartDate && validEndDate && !validDateRange) {
        setEndDateError(true);
        setEndDateLabel("invalid date range");
      }
    }

    handleChange(startDate, endDate,occurrences);
  }

  const updateStartDate = (startDate) => {
    validateAndUpdateDate(startDate, queryTermGroupOptions.endDate);
  }

  const updateEndDate = (endDate) => {
    validateAndUpdateDate(queryTermGroupOptions.startDate, endDate);
  }

  useEffect(() => {
    if (queryTermGroupOptions.startDate !== null) {
      setInitialExpand(true);
    }else{
      setInitialExpand(false);
    }

    if (queryTermGroupOptions.endDate !== null) {
      setInitialExpand(true);
    }else{
      setInitialExpand(false);
    }

    if (Number.isInteger(queryTermGroupOptions.occurrences) &&
        queryTermGroupOptions.occurrences !== 1)
    {
      setInitialExpand(true);
    } else{
      setInitialExpand(false);
    }
  }, [queryTermGroupOptions]);

  useEffect(() => {
      validateAndUpdateDate();
  }, [containsDemographic]);

  return (
    <div
      className={`ConceptListOptions ${multilineClass} ${disabledClass} ${notExpandableClass} ${clearSettingsErrorClass}`}
    >
      <Accordion
        renderAsExpanded={renderGroupAsExpanded || initialExpand}
        forceExpand={containsDemographic && (hasDateRange || hasOccurrences)}
        forceClose={containsDemographic && !(hasDateRange || hasOccurrences)}
        heading={getOptionsDescription()}
        disabled={containsDemographic}
      >
        <DateRange
          minDate={minAcceptableDate}
          startDate={queryTermGroupOptions.startDate}
          startDateError={startDateError}
          startDateLabel={startDateLabel}
          onStartDateChange={(date) => updateStartDate(date)}
          endDate={queryTermGroupOptions.endDate}
          endDateError={endDateError}
          endDateLabel={endDateLabel}
          onEndDateChange={(date) => updateEndDate(date)}
          onClear={resetDateRange}
        />

        {includeOccurrences && (
          <Occurrences
            occurrences={Number.isNaN(queryTermGroupOptions.occurrences) ? "" : queryTermGroupOptions.occurrences}
            onOccurrencesChange={handleOccurrencesChange}
            onResetOccurrences={resetOccurrences}
            error={
              invalidOccurrences || (containsDemographic && hasOccurrences)
            }
            errorText={
              containsDemographic && hasOccurrences
                ? "reset"
                : invalidOccurrences
                ? "required"
                : ""
            }
          />
        )}
      </Accordion>
      {containsDemographic && <DisableRangeOccurrenceHelpTooltip />}
    </div>
  );
};

ConceptListOptions.defaultProps = {
  includeOccurrences: true,
};

ConceptListOptions.propTypes = {
  queryTermGroupOptions: PropTypes.shape(QueryTermGroupOptions.propTypes)
    .isRequired,
  onChange: PropTypes.func.isRequired,
  containsDemographic: PropTypes.bool.isRequired,
  includeOccurrences: PropTypes.bool,
};
