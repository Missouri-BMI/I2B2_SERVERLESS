import React, { useState } from "react";
import PropTypes from "prop-types";
import { Grid } from "@material-ui/core";
import { Draggable } from "react-beautiful-dnd";
import _ from "lodash-uuid";

import "./QueryTermView.scss";
import Popover from "@material-ui/core/Popover";
import { StaticTree } from "components";
import { QueryTerm } from "models";

export const QueryTermView = ({
  term,
  onDeleteTermClicked,
  children,
  fetchConceptInfo,
  index,
}) => {
  const [conceptInfo, setConceptInfo] = useState(null);
  const [conceptInfoOpen, setConceptInfoOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const [uuid] = useState(_.uuid());

  const getConceptInfo = () => {
    (async () => {
      const result = await fetchConceptInfo(term.path);
      const data = result.data;
      setConceptInfo(data);
      setConceptInfoOpen(true);
    })();
  };

  const handlePopoverClose = () => {
    setConceptInfoOpen(false);
    setConceptInfo(null);
  };

  const handlePopoverOpen = (event) => {
    // Set the popup anchor to to the Grid element with the class QueryTermView
    setAnchorEl(event.currentTarget.parentNode.parentNode.parentNode);
    getConceptInfo();
  };

  const extractNodeIds = (concept, nodeIds) => {
    const nodeId = `${concept.conceptType}-${concept.displayName}`;
    nodeIds.push(nodeId);
    if (concept.children) {
      concept.children.forEach((child) => {
        extractNodeIds(child, nodeIds);
      });
    }
  };

  const getNodeIdList = (concept) => {
    const result = [];
    extractNodeIds(concept, result);
    return result;
  };

  return (
    <Draggable draggableId={uuid} index={index} conceptInfo={conceptInfo}>
      {(provided) => (
        <div
          className="QueryTermViewWrapper"
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          ref={provided.innerRef}
        >
          <Grid
            container
            direction="row"
            justify="flex-start"
            className="QueryTermView"
          >
            <Grid xs={2} className="text" item>
              <span className="or">or</span>
              {term.conceptCategory}
            </Grid>
            <Grid className="name" xs={9} item>
              {term.displayName}
              <div className="options">
                <Popover
                  className="branch-popover"
                  open={conceptInfoOpen}
                  anchorEl={anchorEl}
                  onClose={handlePopoverClose}
                  anchorOrigin={{
                    vertical: "bottom",
                    horizontal: "left",
                  }}
                  transformOrigin={{
                    vertical: "top",
                    horizontal: "left",
                  }}
                >
                  <div>
                    {conceptInfoOpen && conceptInfo && (
                      <StaticTree
                        conceptList={[conceptInfo]}
                        nodeList={getNodeIdList(conceptInfo)}
                      />
                    )}
                  </div>
                </Popover>
                <span
                  className="fa fa-info-circle"
                  role="button"
                  onClick={handlePopoverOpen}
                />
                <span
                  className="fa fa-times"
                  role="button"
                  onClick={() => onDeleteTermClicked(term.path)}
                />
              </div>
              {children && (
                <div style={{ display: "flex" }}>
                  <div>{children}</div>
                </div>
              )}
            </Grid>
          </Grid>
        </div>
      )}
    </Draggable>
  );
};

QueryTermView.propTypes = {
  term: PropTypes.shape(QueryTerm.propTypes).isRequired,
  onDeleteTermClicked: PropTypes.func.isRequired,
  children: PropTypes.element,
  index: PropTypes.number.isRequired,
  fetchConceptInfo: PropTypes.func.isRequired,
};

QueryTermView.defaultProps = {
  children: null,
};
