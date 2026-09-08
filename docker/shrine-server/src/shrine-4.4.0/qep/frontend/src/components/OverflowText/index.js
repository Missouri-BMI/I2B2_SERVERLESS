import React, { useRef, useEffect, useState } from "react";
import PropTypes from "prop-types";
import {Link, Collapse, Tooltip} from "@material-ui/core";

const OverflowText = (props) => {
  const { displayHtmlText, showMoreText, showLessText, clipboardCopyFormattedText, clipboardCopyUnformattedText } = props;
  const [showMore, setShowMore] = useState(true);
  const [showLess, setShowLess] = useState(false);

  const textElementRef = useRef();
  const oneLineHeight = 24;

  const [copyTooltipOpen, setCopyTooltipOpen] = useState(false);
  const [copyTooltipText, setCopyTooltipText] = useState(false);

  const handleTooltipOpen = () => {
    setCopyTooltipOpen(true);
    setTimeout(() => {setCopyTooltipOpen(false)}, 2000);
  };

  const copyFormattedText = async (html, text) => {
    try {
      const blobPlain = new Blob([text], { type: 'text/plain' });
      const blob = new Blob([html], { type: 'text/html' });

      const data = [new ClipboardItem({
          [blob.type]: blob,
          [blobPlain.type]: blobPlain
      })];
      await navigator.clipboard.write(data);
      console.log('Copied to clipboard!');
      setCopyTooltipText('Copied to clipboard');
      handleTooltipOpen();
    } catch (err) {
      console.error('Failed to copy text: ', err);
      setCopyTooltipText('Failed to copy text');
      handleTooltipOpen();
    }
  };

  const handleCopy = () => {
    copyFormattedText(clipboardCopyFormattedText, clipboardCopyUnformattedText );
  };

  const compareSize = () => {
    const textHasWrapped = textElementRef.current.scrollHeight > oneLineHeight;

    setShowMore(textHasWrapped);
  };

  useEffect(() => {
    setShowLess(false);
    setShowMore(true);

    compareSize();
  }, [props.displayHtmlText]);

  useEffect(() => {
    window.addEventListener("resize", compareSize);

    return () => {
      window.removeEventListener("resize", compareSize);
    };
  }, []);

  const toggleExpanded = () => {
    setShowMore(!showMore);
    setShowLess(!showLess);
  };

  const copyClipBoard = () => {
    return (
      <Tooltip
      open={copyTooltipOpen}
      title={copyTooltipText}
      placement="top"
      PopperProps={{
        popperOptions: {
          modifiers: {
            offset: {
              enabled: true,
              offset: "0px, -18px",
            },
          },
        },
      }}
      >
        <div className={"CopyToClipboard"} onClick={handleCopy}>
          <i className="fa-solid fa-copy fa-sm"/>
        </div>
      </Tooltip>
    );
  }
  return (
    <div className="OverflowText">
      <Collapse in={showLess} collapsedHeight={oneLineHeight}>
        <div className={"OverflowData"}
          ref={textElementRef}
          dangerouslySetInnerHTML={{ __html: displayHtmlText }}
        />
        {(!showMore && !showLess) && copyClipBoard()}
      </Collapse>
      {(showMore || showLess) && (
        <Link component="button" variant="body2" onClick={toggleExpanded}>
          {(showMore && showMoreText) || (showLess && showLessText)}
        </Link>
      )}
      {(showMore || showLess) && copyClipBoard()}
    </div>
  );
};

export default OverflowText;

OverflowText.defaultProps = {
  displayHtmlText: null
};

OverflowText.propTypes = {
  displayHtmlText: PropTypes.string,
  showLessText: PropTypes.string.isRequired,
  showMoreText: PropTypes.string.isRequired
};
