import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import ExpansionPanel from "@material-ui/core/ExpansionPanel";
import ExpansionPanelDetails from "@material-ui/core/ExpansionPanelDetails";
import CheckIcon from '@material-ui/icons/Check';

import {
  Button,
  Grid,
  Typography,
  Checkbox,
  FormControl,
  Link,
} from "@material-ui/core";

import "./StartQuery.scss";
import QueryNameField from "./QueryNameField";
import {DataDistributionTypesDialog} from "./DataDistributionTypesDialog";

export const StartQuery = ({
  networkName,
  isEnabled,
  onStartQuery,
  dataDistributionTypes,
  initialQueryName,
  generateDefaultQueryName,
}) => {
  const disabled = !isEnabled;
  const [validQueryName, setValidQueryName] = useState(false);
  const [queryName, setQueryName] = useState("");
  const [
    includeDemographicDistribution,
    setIncludeDemographicDistribution,
  ] = useState(false);
  const [selectedDataDistTypes, setSelectedDataDistTypes] = useState([]);
  const validForm = validQueryName;
  const queryNameClassname = queryName.length === 0 ? "" : "valid";
  const [openDataDistTypes, setOpenDataDistTypes] = useState(false);

  // no querNotes here, it's the bottom right form on the landing page
  const onTextChange = (textValue, textValid) => {
    if (textValid) {
      setValidQueryName(true);
    } else {
      setValidQueryName(false);
    }
    setQueryName(textValue);
  };

  const handleIncludeDemographicDistributionsChange = (event) => {
    setIncludeDemographicDistribution(event.target.checked);


    //add the demographic distributions
    if (dataDistributionTypes !== undefined && dataDistributionTypes.allOutputTypes !== undefined
      && dataDistributionTypes.allOutputTypes.Demographic !== undefined) {
      const demographicDistTypes = dataDistributionTypes.allOutputTypes.Demographic.map(d => d.value)

      if (event.target.checked){
        setSelectedDataDistTypes(selectedDataDistTypes.concat(demographicDistTypes));
      }
      else{
        let prunedSelectedDataDistTypes = selectedDataDistTypes.filter(function(item) {
          return demographicDistTypes.indexOf(item) === -1
        });

        setSelectedDataDistTypes(prunedSelectedDataDistTypes);
      }
    }
  };

  const onGenerateDefaultQueryName = () => {
    setQueryName(generateDefaultQueryName());
    setValidQueryName(true);
  };

  const onClick = () => {
    if (validForm) {
      onStartQuery(queryName.trim(), selectedDataDistTypes);
    }
  };

  const handleOpenDataDistTypes = () => {
    setOpenDataDistTypes(true);
  };
  const handleCloseDataDistTypes = (submit, selectedDataDistTypes) => {
    setOpenDataDistTypes(false);
    if(submit) {
      const demographicDistTypes = getDemoDistTypes();
      setSelectedDataDistTypes(selectedDataDistTypes);
      let demoDistTypes = selectedDataDistTypes.filter(function(item) {
        return demographicDistTypes.indexOf(item) !== -1
      });

      if(demoDistTypes.length > 0){
        //demographic distribution types are selected so check the include demo dist checkbox
        setIncludeDemographicDistribution(true);
      }else{
        setIncludeDemographicDistribution(false);
      }
    }
  };
  const getDemoDistTypes = () => {
    return dataDistributionTypes.allOutputTypes.Demographic === undefined ? {} :
      dataDistributionTypes.allOutputTypes.Demographic.map(d => d.value);
  }

  const hasAdvancedDataDistSelected = () => {
    const demographicDistTypes = getDemoDistTypes();

    let advancedDistTypes = selectedDataDistTypes.filter(function(item) {
      return demographicDistTypes.indexOf(item) === -1
    });

    return selectedDataDistTypes.length > 0 &&  advancedDistTypes.length > 0;
  };

  useEffect(() => {
    // A non-empty query name will only be passed to this component when reloading a
    // previously ran query so it can be assumed to be a valid query name.
    const wasQueryLoadedFromHistory = !!initialQueryName;

    if (wasQueryLoadedFromHistory) {
      setQueryName(initialQueryName);
      setValidQueryName(true);
    }
  }, [initialQueryName]);

  return (
    <div className="StartQuery tutorialStep4">
      <Typography className="search-title">{`Search ${networkName} network`}</Typography>

      <ExpansionPanel expanded className="search-form">
        <ExpansionPanelDetails>
          <div className="startQueryForm">
            <div className="startQueryColumn">
              <Grid container direction="row" alignItems="baseline">
                <FormControl
                  className={`query-name-control ${queryNameClassname}`}
                >
                  <QueryNameField
                    label="Enter name"
                    onTextChange={onTextChange}
                    onGenerateName={onGenerateDefaultQueryName}
                    value={queryName}
                  />
                </FormControl>
              </Grid>
            </div>
            <div className="startQueryColumn">
              <div className="demographic-distribution">
                <Checkbox
                  className="demograpic-distribution-checkbox"
                  onChange={handleIncludeDemographicDistributionsChange}
                  checked={includeDemographicDistribution}
                />
                <div className="demo-distribution-label">
                  Include demographic
                  <div>distributions</div>
                </div>
                <div className="advanced-distribution">
                  <Link href="#" onClick={handleOpenDataDistTypes}>advanced options
                    { hasAdvancedDataDistSelected() && <CheckIcon/> }
                  </Link>
                </div>

                {openDataDistTypes && <DataDistributionTypesDialog
                  open={openDataDistTypes}
                  onClose={handleCloseDataDistTypes}
                  dataDistributionTypes={dataDistributionTypes}
                  currentDataDistSelection={selectedDataDistTypes}
                />}
              </div>
            </div>
            <div className="startQueryColumn">
              <Button
                type="button"
                className="btn-primary btn count-button"
                onClick={onClick}
                disabled={disabled || !validForm}
              >
                Count Patients
              </Button>
            </div>
          </div>
        </ExpansionPanelDetails>
      </ExpansionPanel>
    </div>
  );
};

StartQuery.defaultProps = {
  initialQueryName: null,
};

StartQuery.propTypes = {
  networkName: PropTypes.string.isRequired,
  isEnabled: PropTypes.bool.isRequired,
  onStartQuery: PropTypes.func.isRequired,
  dataDistributionTypes: PropTypes.shape({
    isFetching: PropTypes.bool,
  }).isRequired,
  initialQueryName: PropTypes.string,
  generateDefaultQueryName: PropTypes.func.isRequired,
};
