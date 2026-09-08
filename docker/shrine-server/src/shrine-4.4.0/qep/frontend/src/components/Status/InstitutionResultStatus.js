import React, {useState} from "react";
import PropTypes from "prop-types";

import {
  TableCell, TableRow, Typography,
} from "@material-ui/core";
import { InstitutionResult } from "models";
import { getCount } from "utilities";
import { Status } from "./Status";
import { ClickableStatus } from "./ClickableStatus";
import {ResultMetadataDialog} from "./ResultMetadataDialog";

export const InstitutionResultStatus = ({ result }) => {
  const { internalStatus, status } = result;
  const [ showResultMetadata, setShowResultMetadata ] = useState(false);

  let resultType;
  switch (internalStatus.toUpperCase()) {
    case "RESULT FROM CRC":
    case "FINISHED":
        let noiseClamp;
        let lowLimit;
        if( result.resultMetadata.obfuscatingParameters) {
          noiseClamp = result.resultMetadata.obfuscatingParameters.noiseClamp;
          lowLimit = result.resultMetadata.obfuscatingParameters.lowLimit;
        }
        resultType = (<Status statusText={getCount(result.count, noiseClamp, lowLimit)} className="PatientCount" />);

      break;
    case "ID ASSIGNED":
    case "SENT TO ADAPTER":
    case "RECEIVED BY ADAPTER":
    case "READY TO SUBMIT":
    case "SUBMITTED TO CRC":
      resultType = <Status statusText={status} className="ProcessingAtSite" />;
      break;
    case "ERROR IN SHRINE":
    case "UKNOWN WHILE QUEUED BY CRC":
    case "ERROR FROM CRC":
    case "UNKNOWN FINAL":
    case "ERROR":
    case "PROCESSING":
    case "RUNNING":
    case "QUEUED":
    case "INCOMPLETE":
    case "HELD":
    case "SMALL_QUEUE":
    case "TIMEDOUT":
    case "MEDIUM_QUEUE":
    case "LARGE_QUEUE":
    case "NO_MORE_QUEUE":
    case "MEDIUM_QUEUE_RUNNING":
    case "LARGE_QUEUE_RUNNING":
    case "HUB_WILL_SUBMIT":
      resultType = (
        <ClickableStatus
          statusText={status}
          className="SiteError"
          data={result.errorDetailInfo}
        />
      );
      break;
    default:
      resultType = <Status statusText={status} className="DelayedAtSite" />;
  }

  const handleShowResultMetadata = () => {
    setShowResultMetadata(true);
  }

  const handleClose = () => {
    setShowResultMetadata(false);
  }
  return (
    <TableRow>
      <TableCell>
        <Typography>{result.institutionName}</Typography>
      </TableCell>
      <TableCell>
        <Typography>
          {resultType}
          { (result.resultMetadata.obfuscatingParameters || result.resultMetadata.custom) &&
            <span className="result-metadata-btn" onClick={handleShowResultMetadata} role="button">
              <i className="fa fa-info-circle" aria-hidden="true"/>
            </span>
          }
        </Typography>
        <ResultMetadataDialog institutionName={result.institutionName} resultMetadata={result.resultMetadata} onClose={handleClose} open={showResultMetadata} />
      </TableCell>
    </TableRow>
  );
};

InstitutionResultStatus.propTypes = {
  result: PropTypes.shape(InstitutionResult.propTypes).isRequired
};
