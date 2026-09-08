import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";

import {
  Dialog,
  Button,
  Checkbox,
  Divider,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormLabel,
  Typography,
  Backdrop,
} from "@material-ui/core";

import { DataDistributionTypes } from "models";
import "./DataDistributionTypesDialog.scss";


export const DataDistributionTypesDialog = ({
                              open,
                              onClose,
                              dataDistributionTypes,
                              currentDataDistSelection,
                           }) => {
  const [selectedDataDistTypes, setSelectedDataDistTypes] = useState(currentDataDistSelection);
  const demographicDistType = dataDistributionTypes.allOutputTypes['Demographic'];
  const {Demographic, ...nonDemographicDistType }  = dataDistributionTypes.allOutputTypes;

  const handleSetDataDistTypes = () => {
    onClose(true, selectedDataDistTypes);
  };

  const updateSelectedDataDistTypes = (event) => {
    let targetValue = event.target.value;
    let updatedDataDistTypes = [];

    if(event.target.checked)
    {
      updatedDataDistTypes.push(targetValue);
      updatedDataDistTypes = updatedDataDistTypes.concat(selectedDataDistTypes);
    }else{
      updatedDataDistTypes = selectedDataDistTypes.filter(function(item) {
        return item !== targetValue;
      });
    }
    setSelectedDataDistTypes(updatedDataDistTypes);
  };

  const isSelected = (dataDistTypeValue) => {
    return selectedDataDistTypes.indexOf(dataDistTypeValue) !== -1;
  };


  const handleCancel = () => {
    onClose(false);
  };

  useEffect(() => {
  }, []);

  return (
    <Dialog
      aria-labelledby="spring-modal-title"
      aria-describedby="spring-modal-description"
      className="DataDistributionTypesDialog"
      open={open}
      onClose={handleCancel}
      closeAfterTransition
      BackdropComponent={Backdrop}
      BackdropProps={{
        timeout: 500,
      }}
    >
      <div className="DialogContent">
        <span className="close-button" onClick={handleCancel} role="button">
          <i className="fa fa-times" aria-hidden="true" />
        </span>
        <div className="title-label-wrapper">
          <Typography className="title">Select Data Distribution</Typography>
        </div>
        <div className="data-dist-type-content">
          <FormControl className="field-set" component="fieldset">
            <FormLabel className="field-label" component="legend">Demographic</FormLabel>
            <Divider/>
            <FormGroup aria-label="position" col>
              {demographicDistType.map(breakdown => (
                <FormControlLabel className="data-dist-type-label" control={<Checkbox checked={isSelected(breakdown.value)} color="primary"/>} value={breakdown.value} label={breakdown.description} onClick={updateSelectedDataDistTypes}/>
              ))}
            </FormGroup>
          </FormControl>
          {Object.entries(nonDemographicDistType).map(([result,dataKeys ]) => (
              <FormControl className="field-set" component="fieldset">
                <FormLabel className="field-label" component="legend">{result}</FormLabel>
                <Divider/>
                <FormGroup aria-label="position" col>
                {dataKeys.map(breakdown => (
                    <FormControlLabel className="data-dist-type-label" control={<Checkbox checked={isSelected(breakdown.value)} color="primary"/>} value={breakdown.value} label={breakdown.description} onClick={updateSelectedDataDistTypes}/>
                ))}
                </FormGroup>
              </FormControl>
          ))}

        </div>
        <div className="button-row">
          <Button
            onClick={handleSetDataDistTypes}
            className="btn-primary"
          >
            Ok
          </Button>
          <Button className="btn-secondary" onClick={handleCancel}>
            Cancel
          </Button>
        </div>
      </div>
    </Dialog>
  );
};

DataDistributionTypesDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  dataDistributionTypes: PropTypes.shape(DataDistributionTypes.propTypes).isRequired,
};
