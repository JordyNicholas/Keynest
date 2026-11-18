import { maxPasswordLength, minPasswordLength } from './password-options';

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
    forbidUsernameSubstring: boolean;
    bannedSubstrings: string[];
  };
};

export function generatorOptionsForPolicy(rules: BuiltInPolicy['rules']) {
  return {
    minLength: Math.max(minPasswordLength, rules.minLength),
    maxLength: Math.min(maxPasswordLength, rules.maxLength),
    requiredGroups: {
      lower: rules.requireLower,
      upper: rules.requireUpper,
      digits: rules.requireDigit,
      symbols: rules.requireSymbol,
    },
    minCategories: rules.minCategories,
    excludeAmbiguous: rules.excludeAmbiguous,
  };
}
