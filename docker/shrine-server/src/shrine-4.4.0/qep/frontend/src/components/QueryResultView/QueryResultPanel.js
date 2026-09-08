import React from "react";
import PropTypes from "prop-types";

import { Card, Grid, Typography } from "@material-ui/core";
import { QueryStatus } from "../Status";
import "./QueryResultPanel.scss";
import OverflowText from "../OverflowText";
import { SelectedQuery } from "../../models";
import { CountCSVExport } from "./CountCSVExport";

const SubjectBlock = (props) => {
  const { name } = props;
  return (
    <Grid item>
      <div style={{ width: "135px" }}>
        <Typography className="query-result-panel-text">
          {`${name}:`}
        </Typography>
      </div>
    </Grid>
  );
};

export const QueryResultPanel = ({ selectedQuery, dispatch }) => {
  const {
    queryName,
    changeDate,
    queryAsHtmlString,
  } = selectedQuery.queryResult;

  const toDateFormat = (epoch) => {
    const epochDate = new Date(epoch);
    const date = epochDate.toLocaleDateString(undefined, {
      month: "numeric",
      day: "numeric",
      year: "2-digit",
    });
    const time = epochDate.toLocaleTimeString(undefined, {
      timeZoneName: "short",
      hour12: false,
    });
    return `${date} at ${time}`;
  };

  const reformatClipboardCopyHtml = (inputHtml) => {
    let reformattedHtml = "";
    if(inputHtml) {
      reformattedHtml = inputHtml.replaceAll('<span class="criteriaPanelSeparator">', '<b>');
      reformattedHtml = reformattedHtml.replaceAll('</span>', '</b>');
    }
    return reformattedHtml;
  }

  const reformatClipboardCopyText = (inputHtml) => {
    let reformattedText = "";
    if(inputHtml) {
      reformattedText = inputHtml.replaceAll('<span class="criteriaPanelSeparator">', '');
      reformattedText = reformattedText.replaceAll('</span>', '');
      reformattedText = reformattedText.replaceAll('<br/>', '\r\n');
    }
    return reformattedText;
  }

  return (
    <Card className="QueryResultPanel query-result-flex-container">
      <Grid container justify="left">
        <Grid container item>
          <SubjectBlock name="Name" />
          <Grid item xs>
            <Typography className="query-result-panel-text">
              {queryName}
            </Typography>
          </Grid>
          <Grid item>
            {selectedQuery.institutionResults.length > 0 && <CountCSVExport />}
          </Grid>
        </Grid>
        <Grid container item>
          <SubjectBlock name="Criteria" />
          <Grid item xs wrap="nowrap" zeroMinWidth={true}>
            <Grid item xs>
              <Typography className="query-result-panel-text">
                {queryAsHtmlString &&  <OverflowText
                    displayHtmlText={queryAsHtmlString}
                    showMoreText="show more"
                    showLessText="show less"
                    clipboardCopyFormattedText={reformatClipboardCopyHtml(queryAsHtmlString)}
                    clipboardCopyUnformattedText={reformatClipboardCopyText(queryAsHtmlString)}
                  />
                }
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid container item>
          <SubjectBlock name="Status" />
          <Grid item xs>
            <Typography className="query-result-panel-text">
              <QueryStatus selectedQuery={selectedQuery} />
            </Typography>
          </Grid>
        </Grid>
        <Grid container item wrap="nowrap">
          <SubjectBlock name="Last Updated" />
          <Grid item xs>
            <Typography className="query-result-panel-text">
              {changeDate !== null && toDateFormat(changeDate)}
            </Typography>
          </Grid>
        </Grid>
      </Grid>
    </Card>
  );
};

QueryResultPanel.propTypes = {
  selectedQuery: PropTypes.shape(SelectedQuery.propTypes).isRequired,
};

SubjectBlock.propTypes = {
  name: PropTypes.string.isRequired,
};
