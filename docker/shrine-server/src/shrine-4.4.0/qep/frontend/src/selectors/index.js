export const selectSortBy = ({ allQueries }) => allQueries.sortBy;
export const selectLimit = ({ allQueries }) => allQueries.limit;
export const selectSkip = ({ allQueries }) => allQueries.skip;
export const selectSortSiteBy = ({ selectedQuery }) => selectedQuery.sortSiteBy;
export const selectDataVersion = ({ selectedQuery }) =>
  selectedQuery.dataVersion;
export const selectSearchResultsMetadata = ({ ontology }) =>
  ontology.searchResultsMetadata;
