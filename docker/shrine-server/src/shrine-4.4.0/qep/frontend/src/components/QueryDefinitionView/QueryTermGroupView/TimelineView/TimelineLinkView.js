import React, { useState, useEffect } from "react";
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';

import {
  ExpansionPanel,
  ExpansionPanelDetails,
  ExpansionPanelSummary,
  Select,
  MenuItem,
  TextField,
  Checkbox
} from "@material-ui/core";

import {
  TimelineLink,
  TimeSpan,
  TimeSpanUnitTypes,
  TimeSpanOperatorTypes,
  RelationshipTypes,
  EventConstraintAnchor,
  EventConstraintBoundary
} from "models";

import "./TimelineLinkView.scss";

export const TimelineLinkView = ({timelineLink, event1Index, event2Index, updateTimelineLink }) => {
  const [primaryTimeSpan, setPrimaryTimeSpan] = useState(TimeSpan());
  const [secondaryTimeSpan, setSecondaryTimeSpan] = useState(TimeSpan());
  const [updatedTimelineLink, setUpdatedTimelineLink] = useState(TimelineLink());
  const [enablePrimaryTimeSpan, setEnablePrimaryTimeSpan] = useState(false);
  const [enableSecondaryTimeSpan, setEnableSecondaryTimeSpan] = useState(false);

  const boundaryDisplayValueLookup = {};
  boundaryDisplayValueLookup[EventConstraintBoundary.START] = "start";
  boundaryDisplayValueLookup[EventConstraintBoundary.END] = "end";

  const anchorDisplayValueLookup = {};
  anchorDisplayValueLookup[EventConstraintAnchor.FIRST] = "the first ever";
  anchorDisplayValueLookup[EventConstraintAnchor.ANY] = "any";
  anchorDisplayValueLookup[EventConstraintAnchor.LAST] = "the last ever";

  const relationshipTypeDisplayValueLookup = {};
  relationshipTypeDisplayValueLookup[RelationshipTypes.BEFORE] = "occurs before";
  relationshipTypeDisplayValueLookup[RelationshipTypes.BEFOREORSIMULTANEOUS] = "occurs on or before";
  relationshipTypeDisplayValueLookup[RelationshipTypes.SIMULTANEOUS] = "occurs simultaneously with";


  const timeSpanOpDisplayValueLookup = {};
  timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.GREATER] = ">";
  timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.GREATEREQUAL] = ">=";
  timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.EQUAL] = "=";
  timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.LESSEQUAL] = "<=";
  timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.LESS] = "<";

  const timeSpanUnitDisplayValueLookup = {};
  timeSpanUnitDisplayValueLookup[TimeSpanUnitTypes.DAY] = "day(s)"
  timeSpanUnitDisplayValueLookup[TimeSpanUnitTypes.MONTH] = "month(s)"
  timeSpanUnitDisplayValueLookup[TimeSpanUnitTypes.YEAR] = "year(s)"

  const handleTimelineLinkChange = (newTimelineLink) => {
    setUpdatedTimelineLink(newTimelineLink);
    updateTimelineLink({ timelineLinkIndex:event1Index, timelineLink: newTimelineLink });
  }

  const handleOccurrenceChange = (event) => {
    updatedTimelineLink.BasicTimelineLink.relationship = event.target.value;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handlePreviousEventAnchorChange = (event) => {
    updatedTimelineLink.BasicTimelineLink.previousEventConstraint.anchor = event.target.value;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handlePreviousEventBoundaryChange = (event) => {
    updatedTimelineLink.BasicTimelineLink.previousEventConstraint.boundary = event.target.value;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleThisEventConstraintAnchorChange = (event) => {
    updatedTimelineLink.BasicTimelineLink.thisEventConstraint.anchor = event.target.value;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleThisEventConstraintBoundaryChange = (event) => {
    updatedTimelineLink.BasicTimelineLink.thisEventConstraint.boundary = event.target.value;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEventPrimaryTimeSpanOperatorChange = (event) => {
    primaryTimeSpan.operator = event.target.value;
    setPrimaryTimeSpan(primaryTimeSpan);

    updatedTimelineLink.BasicTimelineLink.primaryTimeSpan = primaryTimeSpan;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEventPrimaryTimeSpanValueChange = (event) => {
    primaryTimeSpan.value = event.target.value;
    primaryTimeSpan.value = preventNonIntegerInput(primaryTimeSpan.value);
    setPrimaryTimeSpan(primaryTimeSpan);

    updatedTimelineLink.BasicTimelineLink.primaryTimeSpan = primaryTimeSpan;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEventPrimaryTimeSpanUnitsChange = (event) => {
    primaryTimeSpan.unit = event.target.value;
    setPrimaryTimeSpan(primaryTimeSpan);

    updatedTimelineLink.BasicTimelineLink.primaryTimeSpan = primaryTimeSpan;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEventSecondaryTimeSpanOperatorChange = (event) => {
    secondaryTimeSpan.operator = event.target.value;
    setSecondaryTimeSpan(secondaryTimeSpan);
    updatedTimelineLink.BasicTimelineLink.secondaryTimeSpan = secondaryTimeSpan;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEventSecondaryTimeSpanValueChange = (event) => {
    secondaryTimeSpan.value = event.target.value;
    secondaryTimeSpan.value = preventNonIntegerInput(secondaryTimeSpan.value);
    setSecondaryTimeSpan(secondaryTimeSpan);

    updatedTimelineLink.BasicTimelineLink.secondaryTimeSpan = secondaryTimeSpan;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEventSecondaryTimeSpanUnitsChange = (event) => {
    secondaryTimeSpan.unit = event.target.value;
    setSecondaryTimeSpan(secondaryTimeSpan);
    updatedTimelineLink.BasicTimelineLink.secondaryTimeSpan = secondaryTimeSpan;
    handleTimelineLinkChange(updatedTimelineLink);
  };

  const handleEnablePrimaryTimeSpan = (event) => {
    setEnablePrimaryTimeSpan(event.target.checked);

    if(!event.target.checked){
      setPrimaryTimeSpan(TimeSpan());
      setSecondaryTimeSpan(TimeSpan());
      setEnableSecondaryTimeSpan(false);
      updatedTimelineLink.BasicTimelineLink.primaryTimeSpan = null;
      updatedTimelineLink.BasicTimelineLink.secondaryTimeSpan = null;

      handleTimelineLinkChange(updatedTimelineLink);
    }else{
      updatedTimelineLink.BasicTimelineLink.primaryTimeSpan = primaryTimeSpan;
    }
  };

  const handleEnableSecondaryTimeSpan = (event) => {
    setEnableSecondaryTimeSpan(event.target.checked);

    if(!event.target.checked){
      setSecondaryTimeSpan(TimeSpan());
      updatedTimelineLink.BasicTimelineLink.secondaryTimeSpan = null;
      handleTimelineLinkChange(updatedTimelineLink);
    }else{
      updatedTimelineLink.BasicTimelineLink.secondaryTimeSpan = secondaryTimeSpan;
    }
  };

  const setStateFromProps = () => {
    if(timelineLink.BasicTimelineLink.primaryTimeSpan) {
      setPrimaryTimeSpan(timelineLink.BasicTimelineLink.primaryTimeSpan);
      setEnablePrimaryTimeSpan(true);
    }

    if(timelineLink.BasicTimelineLink.secondaryTimeSpan) {
      setSecondaryTimeSpan(timelineLink.BasicTimelineLink.secondaryTimeSpan);
      setEnableSecondaryTimeSpan(true);
    }

    setUpdatedTimelineLink(timelineLink);
  };

  useEffect(setStateFromProps, [timelineLink]);

  const preventNonIntegerInput = (value) => {
    return parseInt(value.replace(/\D/, ""), 10);
  };

  const isInvalidPrimaryTimeSpanValue = primaryTimeSpan.value.length === 0 || Number.isNaN(primaryTimeSpan.value);
  const isInvalidSecondaryTimeSpanValue = secondaryTimeSpan.value.length === 0 || Number.isNaN(secondaryTimeSpan.value);

  const getInvalidNumberInputText = (value) => {
    if(value.length === 0){
      return "required"
    }
    else{
      return "";
    }
  };

  const getInvalidPrimaryTimeSpanValueText = getInvalidNumberInputText(primaryTimeSpan.value);
  const getInvalidSecondaryTimeSpanValueText = getInvalidNumberInputText(secondaryTimeSpan.value);

  return (

    <div className="TimelineLink">
      <ExpansionPanel>
        <ExpansionPanelSummary expandIcon={<ExpandMoreIcon />}>
          <div className="copy summary">The</div>
          <div className="copy summary">{boundaryDisplayValueLookup[timelineLink.BasicTimelineLink.previousEventConstraint.boundary]}</div>
          <div className="copy summary">of</div>
          <div className="copy summary">{anchorDisplayValueLookup[timelineLink.BasicTimelineLink.previousEventConstraint.anchor]}</div>
          <div className="copy summary">occurrence of Event {event1Index+1}</div>
          <div className="copy summary">{relationshipTypeDisplayValueLookup[timelineLink.BasicTimelineLink.relationship]}</div>
          <div className="copy summary">the</div>
          <div className="copy summary">{boundaryDisplayValueLookup[timelineLink.BasicTimelineLink.thisEventConstraint.boundary]}</div>
          <div className="copy summary">of</div>
          <div className="copy summary">{anchorDisplayValueLookup[timelineLink.BasicTimelineLink.thisEventConstraint.anchor]}</div>
          <div className="copy summary">occurrence of Event {event2Index+1}</div>
          { enablePrimaryTimeSpan && ( <div className="inline-flex">
              <div className="copy summary">by</div>
              <div className="copy summary">{timeSpanOpDisplayValueLookup[primaryTimeSpan.operator]}</div>
              <div className={ isInvalidPrimaryTimeSpanValue ? "invalid-error copy summary" : "copy summary"}>{isInvalidPrimaryTimeSpanValue ? "INVALID" : primaryTimeSpan.value}</div>
              <div className="copy summary">{timeSpanUnitDisplayValueLookup[primaryTimeSpan.unit]}</div>
            </div>
          )}
          { enableSecondaryTimeSpan && ( <div className="copy inline-flex">
              <div className="copy summary">and</div>
              <div className="copy summary">{timeSpanOpDisplayValueLookup[secondaryTimeSpan.operator]}</div>
              <div className={ isInvalidSecondaryTimeSpanValue ? "invalid-error copy summary" : "copy summary"}>{isInvalidSecondaryTimeSpanValue ? "INVALID" : secondaryTimeSpan.value}</div>
              <div className="copy summary">{timeSpanUnitDisplayValueLookup[secondaryTimeSpan.unit]}</div>
            </div>
          )}
        </ExpansionPanelSummary>
        <ExpansionPanelDetails>
          <div className="timelineLinkRow">
            <div className="copy">The</div>
            <Select
              className="occurrence-dropdown"
              value={timelineLink.BasicTimelineLink.previousEventConstraint.boundary}
              onChange={handlePreviousEventBoundaryChange}
            >
              <MenuItem value={EventConstraintBoundary.START}>{boundaryDisplayValueLookup[EventConstraintBoundary.START]}</MenuItem>
              <MenuItem value={EventConstraintBoundary.END}>{boundaryDisplayValueLookup[EventConstraintBoundary.END]}</MenuItem>
            </Select>

            <div className="copy">of</div>

            <Select
              className="occurrence-dropdown"
              value={timelineLink.BasicTimelineLink.previousEventConstraint.anchor}
              onChange={handlePreviousEventAnchorChange}
            >
              <MenuItem value={EventConstraintAnchor.FIRST}>{anchorDisplayValueLookup[EventConstraintAnchor.FIRST]}</MenuItem>
              <MenuItem value={EventConstraintAnchor.ANY}>{anchorDisplayValueLookup[EventConstraintAnchor.ANY]}</MenuItem>
              <MenuItem value={EventConstraintAnchor.LAST}>{anchorDisplayValueLookup[EventConstraintAnchor.LAST]}</MenuItem>
            </Select>

            <div className="copy">occurrence of Event {event1Index+1}</div>
            <Select
              className="occurrence-dropdown"
              value={updatedTimelineLink.BasicTimelineLink.relationship}
              onChange={handleOccurrenceChange}
            >
              <MenuItem value={RelationshipTypes.BEFORE}>{relationshipTypeDisplayValueLookup[RelationshipTypes.BEFORE]}</MenuItem>
              <MenuItem value={RelationshipTypes.BEFOREORSIMULTANEOUS}>{relationshipTypeDisplayValueLookup[RelationshipTypes.BEFOREORSIMULTANEOUS]}</MenuItem>
              <MenuItem value={RelationshipTypes.SIMULTANEOUS}>{relationshipTypeDisplayValueLookup[RelationshipTypes.SIMULTANEOUS]}</MenuItem>
            </Select>

            <div className="copy">the</div>

            <Select
              className="occurrence-dropdown"
              value={updatedTimelineLink.BasicTimelineLink.thisEventConstraint.boundary}
              onChange={handleThisEventConstraintBoundaryChange}
            >
              <MenuItem value={EventConstraintBoundary.START}>{boundaryDisplayValueLookup[EventConstraintBoundary.START]}</MenuItem>
              <MenuItem value={EventConstraintBoundary.END}>{boundaryDisplayValueLookup[EventConstraintBoundary.END]}</MenuItem>
            </Select>
            <div className="copy">of</div>
            <Select
              className="occurrence-dropdown"
              value={updatedTimelineLink.BasicTimelineLink.thisEventConstraint.anchor}
              onChange={handleThisEventConstraintAnchorChange}
            >
              <MenuItem value={EventConstraintAnchor.FIRST}>{anchorDisplayValueLookup[EventConstraintAnchor.FIRST]}</MenuItem>
              <MenuItem value={EventConstraintAnchor.ANY}>{anchorDisplayValueLookup[EventConstraintAnchor.ANY]}</MenuItem>
              <MenuItem value={EventConstraintAnchor.LAST}>{anchorDisplayValueLookup[EventConstraintAnchor.LAST]}</MenuItem>
            </Select>
            <div className="copy">occurrence of Event {event2Index+1}</div>

              <div className="timeSpan">
                <div className="enable-occurrence">
                  <Checkbox
                    onChange={handleEnablePrimaryTimeSpan}
                    checked={enablePrimaryTimeSpan}
                  />
                </div>
                <div className="copy">by</div>
                <Select
                  className="occurrence-dropdown time-unit"
                  value={primaryTimeSpan.operator}
                  onChange={handleEventPrimaryTimeSpanOperatorChange}
                  disabled={!enablePrimaryTimeSpan}
                >
                  <MenuItem value={TimeSpanOperatorTypes.GREATER}>{timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.GREATER]}</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.GREATEREQUAL}>{timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.GREATEREQUAL]}</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.EQUAL}>{timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.EQUAL]}</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.LESSEQUAL}>{timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.LESSEQUAL]}</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.LESS}>{timeSpanOpDisplayValueLookup[TimeSpanOperatorTypes.LESS]}</MenuItem>
                </Select>

                <div>
                  <TextField
                    className={ isInvalidPrimaryTimeSpanValue ? "input error" : "input"}
                    placeholder="value"
                    value={isInvalidPrimaryTimeSpanValue ? "" : primaryTimeSpan.value}
                    InputLabelProps={{
                      shrink: true
                    }}
                    error={isInvalidPrimaryTimeSpanValue}
                    helperText={getInvalidPrimaryTimeSpanValueText}
                    defaultValue={primaryTimeSpan.value}
                    onChange={handleEventPrimaryTimeSpanValueChange}
                    disabled={!enablePrimaryTimeSpan}
                  />
                </div>
                <div>
                  <Select
                    className="occurrence-dropdown time-unit"
                    value={primaryTimeSpan.unit}
                    onChange={handleEventPrimaryTimeSpanUnitsChange}
                    disabled={!enablePrimaryTimeSpan}
                  >
                    <MenuItem value={TimeSpanUnitTypes.DAY}>day(s)</MenuItem>
                    <MenuItem value={TimeSpanUnitTypes.MONTH}>month(s)</MenuItem>
                    <MenuItem value={TimeSpanUnitTypes.YEAR}>year(s)</MenuItem>
                  </Select>
                </div>
              </div>
              <div className="timeSpan">
                <div className="enable-occurrence">
                  <Checkbox
                    onChange={handleEnableSecondaryTimeSpan}
                    checked={enableSecondaryTimeSpan}
                    disabled={!enablePrimaryTimeSpan}
                  />
                </div>

                <div className={enableSecondaryTimeSpan ? "copy" : "copy disabled"}>and</div>
                <Select
                  className="occurrence-dropdown time-unit"
                  value={secondaryTimeSpan.operator}
                  onChange={handleEventSecondaryTimeSpanOperatorChange}
                  disabled={!enableSecondaryTimeSpan}
                >
                  <MenuItem value={TimeSpanOperatorTypes.GREATER}>&gt;</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.GREATEREQUAL}>&gt;=</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.EQUAL}>=</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.LESSEQUAL}>&lt;=</MenuItem>
                  <MenuItem value={TimeSpanOperatorTypes.LESS}>&lt;</MenuItem>
                </Select>

                <div>
                  <TextField
                    className={isInvalidSecondaryTimeSpanValue ? "input error" : "input"}
                    placeholder="value"
                    value={isInvalidSecondaryTimeSpanValue ? "" : secondaryTimeSpan.value}
                    InputLabelProps={{
                      shrink: true,
                    }}
                    error={isInvalidSecondaryTimeSpanValue}
                    helperText={getInvalidSecondaryTimeSpanValueText}
                    defaultValue={secondaryTimeSpan.value}
                    onChange={handleEventSecondaryTimeSpanValueChange}
                    disabled={!enableSecondaryTimeSpan}
                  />
                </div>
                <div>
                  <Select
                    className="occurrence-dropdown time-unit"
                    value={secondaryTimeSpan.unit}
                    onChange={handleEventSecondaryTimeSpanUnitsChange}
                    disabled={!enableSecondaryTimeSpan}
                  >
                    <MenuItem value={TimeSpanUnitTypes.DAY}>{timeSpanUnitDisplayValueLookup[TimeSpanUnitTypes.DAY]}</MenuItem>
                    <MenuItem value={TimeSpanUnitTypes.MONTH}>{timeSpanUnitDisplayValueLookup[TimeSpanUnitTypes.MONTH]}</MenuItem>
                    <MenuItem value={TimeSpanUnitTypes.YEAR}>{timeSpanUnitDisplayValueLookup[TimeSpanUnitTypes.YEAR]}</MenuItem>
                  </Select>
                </div>
              </div>
          </div>
        </ExpansionPanelDetails>
      </ExpansionPanel>
    </div>
  );
};
