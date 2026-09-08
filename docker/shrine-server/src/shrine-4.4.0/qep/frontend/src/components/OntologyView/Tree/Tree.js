import React, { useState } from "react";
import PropTypes from "prop-types";
import TreeView from "@material-ui/lab/TreeView";

import "./Tree.scss";
import Branch from "./Branch";

export function Tree({ conceptList }) {
  const [expanded, setExpanded] = useState([]);

  const expandNode = nodeId => {
    if (!expanded.includes(nodeId)) {
      setExpanded(prevExpanded => [...prevExpanded, nodeId]);
    }
  };

  const collapseNode = nodeId => {
    if (expanded.includes(nodeId)) {
      setExpanded(prevExpanded => prevExpanded.filter(id => id !== nodeId));
    }
  };

  return (
    <>
      <TreeView className="Tree" expanded={expanded}>
        {conceptList.map(concept => (
          <Branch
            concept={concept}
            key={`${concept.conceptType}-${concept.displayName}`}
            nodeId={`${concept.conceptType}-${concept.displayName}`}
            expandNode={expandNode}
            collapseNode={collapseNode}
          />
        ))}
      </TreeView>
    </>
  );
}

Tree.propTypes = {
  conceptList: PropTypes.arrayOf(
    PropTypes.shape({
      displayName: PropTypes.string
    })
  ).isRequired
};
