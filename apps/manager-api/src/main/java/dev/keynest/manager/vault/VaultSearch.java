package dev.keynest.manager.vault;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

record VaultSearch(int page, int size, String query, Field field, Expiry expiry, Order order, UUID itemId) {
  enum Field { ALL, LABEL, URL, USERNAME }
  enum Expiry { ALL, ATTENTION, EXPIRED, SOON, UPCOMING, LATER, UNSET }
  enum Order { LABEL, EXPIRY }

  static VaultSearch of(VaultQueryService.SearchRequest body) {
    int page = body.page() == null ? 0 : body.page();
    int size = body.size() == null ? 10 : body.size();
    if (page < 0 || page > 100_000 || !Set.of(10, 25, 30, 50).contains(size)) throw invalid();
    if (body.query() != null && body.query().length() > 200) throw invalid();
    String query = body.query() == null ? "" : body.query().strip().toLowerCase(Locale.ROOT);
    return new VaultSearch(page, size, query, parse(Field.class, body.field(), Field.ALL),
      parse(Expiry.class, body.expiry(), Expiry.ALL), parse(Order.class, body.sort(), Order.LABEL), body.itemId());
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
    if (value == null) return fallback;
    try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
    catch (IllegalArgumentException failure) { throw invalid(); }
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Vault search options");
  }
}
