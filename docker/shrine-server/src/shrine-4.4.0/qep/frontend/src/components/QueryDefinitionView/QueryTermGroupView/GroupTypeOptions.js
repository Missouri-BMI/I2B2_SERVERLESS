import React from "react";
import PropTypes from "prop-types";
import {
  Card,
  Grid,
  RadioGroup,
  Radio,
  FormControlLabel,
} from "@material-ui/core";

import { QueryTermGroupStatusTypes } from "models";
import "./GroupTypeOptions.scss";

export default function GroupTypeOptions({
                                      onRadioChange,
                                      activeClass,
                                      onClearAllTermsClicked,
                                      hasChildren,
                                      status,
                                      timelineOptionDisabled,
                                    }) {
  return (
    <Card className={`GroupTypeOptions ${activeClass}`}>
      <Grid
        className="inclusion"
        container
        direction="row"
        justify="center"
        alignItems="center"
      >
        <Grid item xs={4} className="find-patients">
          Find patients
        </Grid>
        <Grid item xs={4} className="and">
          and
        </Grid>
        <Grid item xs={4}>
          <RadioGroup aria-label="with" name="with" className="with-selector">
            <FormControlLabel
              className="label with"
              control={<Radio color="primary" />}
              label="with"
              value={QueryTermGroupStatusTypes.INCLUDED}
              onChange={onRadioChange}
              checked={status === QueryTermGroupStatusTypes.INCLUDED}
            />
            <FormControlLabel
              className="label without"
              control={<Radio color="primary" />}
              label="without"
              value={QueryTermGroupStatusTypes.EXCLUDED}
              onChange={onRadioChange}
              checked={status === QueryTermGroupStatusTypes.EXCLUDED}
            />
            <FormControlLabel
              className="label when"
              control={<Radio color="primary" />}
              label="when"
              value={QueryTermGroupStatusTypes.TIMELINE}
              onChange={onRadioChange}
              checked={status === QueryTermGroupStatusTypes.TIMELINE}
              disabled={timelineOptionDisabled}
            />
          </RadioGroup>
        </Grid>
      </Grid>
      <span className="card-clearer-container">
        <span
          aria-hidden="true"
          className="card-clearer"
          role="button"
          onClick={onClearAllTermsClicked}
          hidden={!hasChildren}
        >
          <i className="fa fa-times" aria-hidden="true" />
        </span>
      </span>
    </Card>
  );
}

GroupTypeOptions.propTypes = {
  onRadioChange: PropTypes.func.isRequired,
  activeClass: PropTypes.string.isRequired,
  onClearAllTermsClicked: PropTypes.func.isRequired,
  hasChildren: PropTypes.bool.isRequired,
};
