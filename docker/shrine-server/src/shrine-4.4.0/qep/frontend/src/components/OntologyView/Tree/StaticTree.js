import React from "react";
import PropTypes from "prop-types";
import TreeView from "@material-ui/lab/TreeView";

import "./StaticTree.scss";
import StaticBranch from "./StaticBranch";

export function StaticTree({ conceptList, nodeList }) {
  return (
    <TreeView className="StaticTree" expanded={nodeList}>
      {conceptList.map(concept => (
        <StaticBranch
          concept={concept}
          key={`${concept.conceptType}-${concept.displayName}`}
          nodeId={`${concept.conceptType}-${concept.displayName}`}
        />
      ))}
    </TreeView>
  );
}

StaticTree.propTypes = {
  conceptList: PropTypes.arrayOf(
    PropTypes.shape({
      displayName: PropTypes.string
    })
  ).isRequired,
  nodeList: PropTypes.arrayOf(PropTypes.string).isRequired
};
