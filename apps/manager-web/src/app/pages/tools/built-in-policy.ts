export type BuiltInPolicy = {
  id: string;
  rules: {
    minLength: number;
    maxLength: number;
    requireUpper: boolean;
    requireLower: boolean;
    requireDigit: boolean;
    requireSymbol: boolean;
    minCategories: number;
    excludeAmbiguous: boolean;
    forbidWhitespace: boolean;
    bannedSubstrings: string[];
  };
};
