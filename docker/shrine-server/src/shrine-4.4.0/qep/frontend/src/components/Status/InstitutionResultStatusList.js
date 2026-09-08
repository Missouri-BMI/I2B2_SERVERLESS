import React from "react";
import PropTypes from "prop-types";

import { Paper, Table, TableContainer, TableBody } from "@material-ui/core";
import { InstitutionResult } from "models";
import { InstitutionResultStatus } from "./InstitutionResultStatus";
import { InstitutionResultHeader } from "./InstitutionResultHeader";
import { Loader } from "components";
import "./InstitutionResultStatusList.scss";

export const InstitutionResultStatusList = (props) => {
  const {  institutionResults, isLoading} = props;

  return (
    <Paper
      elevation={0}
      className="InstitutionResultStatusList query-result-flex-container"
    >
      <TableContainer className="institution-result-container">
        <Table stickyHeader size="small">
          <InstitutionResultHeader/>
          { isLoading ? (
            <Loader />
            ) : (
              <TableBody>
                {
                  institutionResults.map((res) => (
                  <InstitutionResultStatus
                    key={res.institutionName}
                    result={res}
                  />
                  ))
                }
              </TableBody>
            )}
        </Table>
      </TableContainer>
    </Paper>
  );
};

InstitutionResultStatusList.propTypes = {
  institutionResults: PropTypes.arrayOf(InstitutionResult).isRequired,
};
