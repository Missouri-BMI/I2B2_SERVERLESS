import moment from "moment";

export const getDatetime = (value) =>
  moment(Number(value)).format("MM/DD/YYYY");
