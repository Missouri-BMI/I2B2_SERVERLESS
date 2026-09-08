import React, { useState, useEffect } from "react";
import PropTypes from "prop-types";
import {Box, FormControlLabel, Switch, Typography} from "@material-ui/core";

import { QueryResult, InstitutionResultOptions } from "models";
import { QueryResultPanel } from "./QueryResultPanel";
import { InstitutionResultStatusList } from "../Status";
import { QueryResultHeader } from "./QueryResultHeader";
import { QueryResultContext } from "./QueryResultContext";
import { DemographicDistributions } from "./DemographicDistributions";

import "./QueryResultView.scss";

const { sortBy } = InstitutionResultOptions;

export const QueryResultView = ({
  selectedQuery,
  onSiteSort,
  onReloadQuery,
}) => {
  const [sortHeaderId, setSortHeaderId] = useState("site");
  const [sortOrder, setSortOrder] = useState("asc");
  const [allowFilter, setAllowFilter] = useState(true);
  const [siteResults, setSiteResults] = useState([]);
  const [showErrorSites, setShowErrorSites] = useState(false);
  const [errorSitesCount, setErrorSitesCount] = useState(0);

  const onSiteResultSort = (headerId) => {
    if (allowFilter) {
      const useDefaultSortOrder = headerId !== sortHeaderId;
      const newSortOrder =
        useDefaultSortOrder || sortOrder === sortBy.DESC
          ? sortBy.ASC
          : sortBy.DESC;

      setAllowFilter(false);
      setSortHeaderId(headerId);
      setSortOrder(newSortOrder);
      onSiteSort(`${headerId}.${newSortOrder}`);
    }
  };

  const {
    queryId,
    institutionResults,
    isFetching,
    isNewDataFetch,
  } = selectedQuery;

  const onReloadQueryClick = () => {
    if (queryId !== null) {
      onReloadQuery(queryId);
    }
  };
  const isLoading = isFetching && isNewDataFetch;

  useEffect(() => {
    setSortHeaderId("site");
    setSortOrder("asc");
  }, [queryId]);

  useEffect(() => {
    // TODO: This logic will have to change when reimplementing filtering
    setAllowFilter(!isLoading);
  }, [isLoading]);

  const filterErrorSites = (institutionRes) => {
    return institutionRes.filter(i => i.status.toUpperCase() !== "SITE ERROR");
  }

  useEffect(() => {
    const sitesWithoutErrors = filterErrorSites(institutionResults);
    let filteredSitesCount = institutionResults.length - sitesWithoutErrors.length;
    if(!showErrorSites){
      setSiteResults(sitesWithoutErrors);
    }else{
      setSiteResults(institutionResults);
    }
    setErrorSitesCount(filteredSitesCount);
  }, [institutionResults]);

  const handleShowErrorSites = (event) => {
    if(event.target.checked){
      setSiteResults(institutionResults);
    }else{
      const filteredSites = filterErrorSites(institutionResults);
      setSiteResults(filteredSites);
    }
    setShowErrorSites(event.target.checked);
  }

  const noQuerySelectedMarkup = (
    <div className="no-query-message">
      <Typography>
        Select a row from the Previous Results panel to view criteria details
        and patient counts.
      </Typography>
    </div>
  );

  return (
    <Box className="QueryResultView">
      <QueryResultHeader
        disableButton={queryId === null}
        onReloadQueryClick={onReloadQueryClick}
      />
      {queryId === null && noQuerySelectedMarkup}
      {queryId !== null && (
        <Box className="QueryResultContent query-result-flex-container">
          <QueryResultContext.Provider
            value={{ sortHeaderId, sortOrder, onSiteResultSort }}
          >
            <QueryResultPanel selectedQuery={selectedQuery} />
            <div className="results-and-distribution">
              <>
                {selectedQuery.demographicDistribution.length > 0 && (
                  <DemographicDistributions
                    demographicData={selectedQuery.demographicDistribution}
                  />
                )}

                <div className={"ToggleErrorSites"}>
                  <FormControlLabel className={"ToggleSwitch"} control={
                    <Switch
                      checked={showErrorSites}
                      onChange={handleShowErrorSites}
                      size="small"
                      color="primary"
                    />
                    } label={"Show sites with error (" + errorSitesCount + ")"}
                  />
                </div>
                <InstitutionResultStatusList
                  institutionResults={siteResults}
                  isLoading = {isLoading}
                />
              </>
            </div>
          </QueryResultContext.Provider>
        </Box>
      )}
    </Box>
  );
};

QueryResultView.propTypes = {
  selectedQuery: PropTypes.shape(QueryResult.propTypes).isRequired,
  onSiteSort: PropTypes.func.isRequired,
  onReloadQuery: PropTypes.func.isRequired,
};
