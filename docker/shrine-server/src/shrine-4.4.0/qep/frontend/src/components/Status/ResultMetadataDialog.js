import React from "react";
import PropTypes from "prop-types";

import {
  Dialog,
  Button,
  List, ListItem, Link,
} from "@material-ui/core";

import "./ResultMetadataDialog.scss";
import {Typography} from "@material-ui/core";

export const ResultMetadataDialog = ({
  institutionName,
  open,
  onClose,
  resultMetadata,
}) => {

  const getObfuscationDisplayLabel = (key) => {
     let label = key;
      switch (key) {
        case 'binSize' :
          label = 'Obfuscation Bin Size';
          break;
        case 'stdDev' :
          label = 'Obfuscation Standard Deviation';
          break;
        case 'noiseClamp' :
          label = 'Obfuscation Noise Clamp';
          break;
        case 'lowLimit' :
          label = 'Obfuscation Low Limit';
          break;
      }

      return label;
  }

  const extractMetadataList = (label, value) => {
    if(typeof value === 'object' && !Array.isArray(value)) {
      return (
        <>
          <div>
            <i className="fa-solid fa-minus list-icon" aria-hidden="true"/>
            {label}
          </div>
          <List>
            {Object.keys(value).map(key => {
              return (
                <ListItem>
                  { extractMetadataList(key, value[key])}
                </ListItem>
              );
            })}
          </List>
        </>
      )
    }
    else{
      return (<><i className="fa-solid fa-minus list-icon" aria-hidden="true"/>
        { label + " : " + value}</>)
    }
  }

  return (
    <Dialog className="ResultMetadataDialog" open={open} onClose={onClose}>
      <div className="DialogContent">
        <span className="close-button" onClick={onClose} role="button">
          <i className="fa fa-times" aria-hidden="true" />
        </span>
        <div className="ResultMetadataDialogTitleWrapper">
          <Typography className="ResultMetadataDialogTitle">{institutionName} - Metadata</Typography>
        </div>
        { resultMetadata.obfuscatingParameters &&
          <List>
            <div className={"MetadataTitle"}>Obfuscation settings</div>
            <div className={"ObfuscationDescription"}>For more information about obfuscation
              <Link href={"https://open.catalyst.harvard.edu/wiki/display/SHRINE/SHRINE+4.4+Webclient+Help#SHRINE4.4WebclientHelp-ObfuscationParameters"} target="_blank" rel="noopener"> click here</Link>.</div>
            <ListItem>
              <List>
                {
                  Object.entries(resultMetadata.obfuscatingParameters).map(([key, value]) => {
                    return (
                      <ListItem>
                        <i className="fa-solid fa-minus list-icon" aria-hidden="true"/>
                        {getObfuscationDisplayLabel(key) + " : " + value}
                      </ListItem>
                    );
                  })
                }
              </List>
            </ListItem>
          </List>
        }
        { resultMetadata.custom &&
          <List>
            <div className={"MetadataTitle"}>Custom</div>
            {
              Object.entries(resultMetadata.custom).map(([key, value]) => {
                return (
                  <ListItem>
                    {extractMetadataList(key, value)}
                  </ListItem>
                );
              })
            }
          < /List>
        }
        <div className="button-row">
          <Button
            onClick={onClose}
            className="btn-primary submit-button"
          >
            Close
          </Button>
        </div>
      </div>
    </Dialog>
  );
};

ResultMetadataDialog.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  resultMetadata: PropTypes.object.isRequired,
};
