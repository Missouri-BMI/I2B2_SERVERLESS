import React from "react";
import PropTypes from "prop-types";
import { Grid } from "@material-ui/core";

import { QueryTermGroup } from "models";
import QueryTermViewList from "../QueryTermViewList";
import { ConceptListOptions } from "../ConceptListOptions";
import GroupInstructionsPanel from "../GroupInstructionsPanel";
import { InclusionExclusionInstructions } from "./InclusionExclusionInstructions";
import "./InclusionExclusionPanel.scss";

export default function InclusionExclusionPanel({
  group,
  onDeleteTermClicked,
  updateTerm,
  hasChildren,
  onOptionsChange,
  fetchConceptInfo,
}) {
  const handleOptionsChange = (startDate, endDate, occurrences) => {
    group.options.startDate = startDate;
    group.options.endDate = endDate;
    group.options.occurrences = occurrences;

    onOptionsChange(group.id, group.options);
  };

  return (
    <div className="InclusionExclusionPanel">
      <Grid xs={12} item>
        <QueryTermViewList
          groupId={group.id}
          terms={group.concepts}
          onDeleteTermClicked={onDeleteTermClicked}
          updateTerm={(termPath, newOptions) =>
            updateTerm({ id: group.id, termPath, newOptions })
          }
          fetchConceptInfo={fetchConceptInfo}
        />
        <GroupInstructionsPanel hasChildren={hasChildren}>
          <InclusionExclusionInstructions />
        </GroupInstructionsPanel>
        <ConceptListOptions
          containsDemographic={group.containsDemographic}
          queryTermGroupOptions={group.options}
          onChange={handleOptionsChange}
        />
      </Grid>
    </div>
  );
}

InclusionExclusionPanel.propTypes = {
  onDeleteTermClicked: PropTypes.func.isRequired,
  updateTerm: PropTypes.func.isRequired,
  onOptionsChange: PropTypes.func.isRequired,
  group: PropTypes.shape(QueryTermGroup.propTypes).isRequired,
  hasChildren: PropTypes.bool.isRequired,
  fetchConceptInfo: PropTypes.func.isRequired,
};
