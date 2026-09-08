import React from "react";
import PropTypes from "prop-types";

import { ProblemDigest as ProblemDigestModel, WIKI_BASE_URL } from "models";
import { EmailInstructions } from "./EmailInstructions";
import { DetailsText } from "./DetailsText";

export function ProblemDigest({ data, emailTo = null }) {
  const {
    codec,
    summary,
    description,
    stampText,
    detailsText,
    hasDetailsText
  } = data.problemDigest;

  return (
    <div className="StatusBoxText problemDigestContent">
      <p className="instructions">
        <EmailInstructions subject={data.status} emailTo={emailTo} />
      </p>
      <hr />
      <div>
        <b>Summary:</b>
      </div>
      <div>{summary}</div>
      <br />
      <div>
        <b>Description:</b>
      </div>
      <div>
        <p>{description}</p>
      </div>
      <br />
      <span id="pluginMoreDetail">
        <div>
          <b>Codec:</b>
        </div>
        <div>{codec}</div>
        <div>
          <i>
            For information on troubleshooting and resolution, check{" "}
            <a
              href={`${WIKI_BASE_URL}${codec}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              the SHRINE Error Codex
            </a>
            .
          </i>
        </div>
        <br />
        <div>
          <b>Stamp:</b>
        </div>
        <div>{stampText}</div>
        <br />
        <div>
          <b>Name:</b>
        </div>
        <div>{codec}</div>
        <br />
        <div>
          <b>Message:</b>
        </div>
        <div>{description}</div>
        <br />
        <div>
          <b>Details:</b>
        </div>
        <div>
          {hasDetailsText ? (
            <DetailsText detailsText={detailsText} />
          ) : (
            <span>{detailsText}</span>
          )}
        </div>
        <br />
      </span>
    </div>
  );
}

ProblemDigest.defaultProps = {
  emailTo: null
};

ProblemDigest.propTypes = {
  data: PropTypes.shape(ProblemDigestModel.propTypes).isRequired,
  emailTo: PropTypes.string
};
