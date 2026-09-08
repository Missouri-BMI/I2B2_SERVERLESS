import {
  AllQueries,
  QueryDefinition,
  ViewMode,
  Ontology,
  SearchSuggestions,
  NetworkConfig,
  DataDistributionTypes,
  SelectedQuery,
  Session,
  Error as SHRINEError
} from "models";

export const defaultState = {
  selectedQuery: SelectedQuery(),
  allQueries: AllQueries(),
  queryDefinition: QueryDefinition(),
  viewMode: ViewMode(),
  ontology: Ontology(),
  autoSuggestions: SearchSuggestions(),
  session: Session(),
  networkConfig: NetworkConfig(),
  dataDistributionTypes: DataDistributionTypes(),
  unexpectedError: SHRINEError()
};
