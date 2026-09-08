import { configure } from "enzyme";
import Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });

// jsdom does not support this method, while every browser does. https://github.com/jsdom/jsdom/issues/1695
window.HTMLElement.prototype.scrollIntoView = () => {};

global.document.createRange = () => ({
  setStart: () => {},
  setEnd: () => {},
  commonAncestorContainer: {
    nodeName: "BODY",
    ownerDocument: document
  }
});
