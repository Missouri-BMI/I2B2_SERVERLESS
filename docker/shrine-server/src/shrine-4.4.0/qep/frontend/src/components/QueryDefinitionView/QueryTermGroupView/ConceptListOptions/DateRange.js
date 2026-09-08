import React, { useState } from "react";
import PropTypes from "prop-types";
import { Typography, Icon } from "@material-ui/core";
import moment from "moment";
import MomentUtils from "@date-io/moment";
import {
  MuiPickersUtilsProvider,
  KeyboardDatePicker
} from "@material-ui/pickers";

import "./DateRange.scss";

export default function DateRange({
  minDate,
  startDate,
  startDateError,
  startDateLabel,
  onStartDateChange,
  endDate,
  endDateError,
  endDateLabel,
  onEndDateChange,
  onClear
}) {
  return (
    <div className="DateRange">
      <div className="date-range-label">
        <Typography>Date Range:</Typography>
      </div>
      <div className="start-date">
        <MuiPickersUtilsProvider utils={MomentUtils}>
          <KeyboardDatePicker
            minDate={minDate}
            error={startDateError}
            label="Concepts must occur from"
            helperText={startDateLabel}
            autoOk
            onChange={onStartDateChange}
            value={startDate}
            disableToolbar
            variant="inline"
            format="MM/DD/YYYY"
            placeholder="MM/DD/YYYY"
            InputLabelProps={{
              shrink: true
            }}
            KeyboardButtonProps={{
              "aria-label": "change date"
            }}
          />
        </MuiPickersUtilsProvider>
      </div>
      <div className="end-date">
        <MuiPickersUtilsProvider utils={MomentUtils}>
          <KeyboardDatePicker
            minDate={moment(startDate).add(1, "days")}
            error={endDateError}
            className="end-date"
            helperText={endDateLabel}
            autoOk
            label="to"
            onChange={onEndDateChange}
            value={endDate}
            disableToolbar
            variant="inline"
            format="MM/DD/YYYY"
            placeholder="MM/DD/YYYY"
            InputLabelProps={{
              shrink: true
            }}
            KeyboardButtonProps={{
              "aria-label": "change date"
            }}
          />
        </MuiPickersUtilsProvider>
      </div>
      <div className="clear-date-range">
        <Icon className="fa fa-undo" onClick={onClear} />
      </div>
    </div>
  );
}

DateRange.propTypes = {
  minDate: PropTypes.instanceOf(moment),
  startDate: PropTypes.instanceOf(moment),
  onStartDateChange: PropTypes.func.isRequired,
  endDate: PropTypes.instanceOf(moment),
  onEndDateChange: PropTypes.func.isRequired,
  onClear: PropTypes.func.isRequired,
  startDateError: PropTypes.bool,
  startDateLabel: PropTypes.string,
  endDateError: PropTypes.bool,
  endDateLabel: PropTypes.string
};

DateRange.defaultProps = {
  minDate: null,
  startDate: null,
  endDate: null,
  startDateError: false,
  startDateLabel: "",
  endDateError: false,
  endDateLabel: ""
};
