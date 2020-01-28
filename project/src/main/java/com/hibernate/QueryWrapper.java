

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;

/**
 * This class is created just for making it easy to refactoring the deprecated Hibernate-specific
 * Criteria class with JPA API implemented in Hibernate 5.2. It does cover all cases by far.
 */
public class QueryWrapper<T> {
  private static Logger log = LogManager.getLogger(QueryWrapper.class);

  private Session session;
  private CriteriaBuilder builder;
  private CriteriaQuery<T> query;
  private Root<T> root;
  private List<Predicate> pre;
  private String[] select;

  private CriteriaQuery<Long> counterQuery;

  private Expression<?> pathOf(String embeddedAtrAtr) {
    String[] embedded = embeddedAtrAtr.split("\\.");
    return root.join(embedded[0]).get(embedded[1]);
  }

  private <X> CriteriaQuery<X> where(CriteriaQuery<X> cq) {
    if (pre != null) {
      Predicate[] pres = new Predicate[pre.size()];
      pre.toArray(pres);
      cq.where(pres);
    }
    return cq;
  }

  private <X> void select(CriteriaQuery<X> cq) {
    if (select == null) {
      query.select(root);
    } else if (select.length == 1) {
      query.select(root.get(select[0]));
    } else {
      List<Selection<?>> list = new ArrayList<Selection<?>>(select.length);
      for (String s : select) {
        list.add(root.get(s));
      }
      query.multiselect(list);
    }
  }

  private Query<T> getQuery() {
    select(query);
    where(query);
    return session.createQuery(query);
  }

  public QueryWrapper(
      Class<T> className, Session session, boolean forRowAccount, @Nullable String... select) {
    this.session = session;
    builder = session.getCriteriaBuilder();
    query = builder.createQuery(className);

    pre = new ArrayList<>();
    this.select = select;
    if (forRowAccount) {
      counterQuery = builder.createQuery(Long.class);
      root = counterQuery.from(className);
    } else {
      root = query.from(className);
    }
  }

  public QueryWrapper(Class<T> className, Session session) {
    this(className, session, false, (String[]) null);
  }

  public QueryWrapper(Class<T> className, Session session, boolean forRowAccount) {
    this(className, session, forRowAccount, (String[]) null);
  }

  @SuppressWarnings("unchecked")
  public <R> Expression<R> getKey(String column) {
    return column.contains(".") ? (Expression<R>) pathOf(column) : root.get(column);
  }

  public interface PredicateBuilder {
    Expression<Boolean> build(CriteriaBuilder build);
  }

  /**
   * <pre>
   * Developer need know how to create Expression with
   * - object of {@link CriteriaBuilder}.
   * - {@link QueryWrapper#getKey(String)}
   */
  public QueryWrapper<T> or(PredicateBuilder left, PredicateBuilder right) {
    pre.add(builder.or(left.build(builder), right.build(builder)));
    return this;
  }

  public QueryWrapper<T> andIsFalse(String column) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<Boolean> key =
        column.contains(".") ? (Expression<Boolean>) pathOf(column) : root.get(column);
    pre.add(builder.isFalse(key));
    return this;
  }

  public QueryWrapper<T> andIsTrue(String column) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<Boolean> key =
        column.contains(".") ? (Expression<Boolean>) pathOf(column) : root.get(column);
    pre.add(builder.isTrue(key));
    return this;
  }

  public QueryWrapper<T> andEqual(String column, Object with) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    Expression<?> key = column.contains(".") ? pathOf(column) : root.get(column);
    pre.add(builder.equal(key, with));
    return this;
  }

  public QueryWrapper<T> andEqualIgnoreCase(String column, String with) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<String> key =
        column.contains(".") ? (Expression<String>) pathOf(column) : root.get(column);
    pre.add(builder.equal(builder.lower(key), with.toLowerCase()));
    return this;
  }

  public QueryWrapper<T> andNotEqual(String column, Object with) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    Expression<?> key = column.contains(".") ? pathOf(column) : root.get(column);
    pre.add(builder.notEqual(key, with));
    return this;
  }

  public QueryWrapper<T> andNotLike(String column, String like) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<String> key =
        column.contains(".") ? (Expression<String>) pathOf(column) : root.get(column);
    pre.add(builder.notLike(key, like));
    return this;
  }

  public QueryWrapper<T> andIn(String column, Collection<?> scope) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    Expression<?> key = column.contains(".") ? pathOf(column) : root.get(column);
    pre.add(key.in(scope));
    return this;
  }

  public QueryWrapper<T> andLike(String column, String like) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<String> key =
        column.contains(".") ? (Expression<String>) pathOf(column) : root.get(column);
    pre.add(builder.like(key, like));
    return this;
  }

  public QueryWrapper<T> andiLike(String column, String like) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<String> key =
        column.contains(".") ? (Expression<String>) pathOf(column) : root.get(column);
    pre.add(builder.like(builder.lower(key), like.toLowerCase()));
    return this;
  }

  public QueryWrapper<T> andIsNotNull(String column) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    Expression<?> key = column.contains(".") ? pathOf(column) : root.get(column);
    pre.add(builder.isNotNull(key));
    return this;
  }

  public QueryWrapper<T> andIsNull(String column) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    Expression<?> key = column.contains(".") ? pathOf(column) : root.get(column);
    pre.add(builder.isNull(key));
    return this;
  }

  public <Y extends Comparable<? super Y>> QueryWrapper<T> andGreaterThanOrEqualTo(
      String column, Y yl) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<? extends Y> key =
        column.contains(".") ? (Path<Y>) pathOf(column) : root.get(column);
    pre.add(builder.greaterThanOrEqualTo(key, yl));
    return this;
  }

  public <Y extends Comparable<? super Y>> QueryWrapper<T> andLessThanOrEqualTo(
      String column, Y yl) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<? extends Y> key =
        column.contains(".") ? (Path<Y>) pathOf(column) : root.get(column);
    pre.add(builder.lessThanOrEqualTo(key, yl));
    return this;
  }

  public <Y extends Comparable<? super Y>> QueryWrapper<T> andBetween(String column, Y lf, Y rt) {
    Preconditions.checkArgument(column != null && column.length() > 0);
    @SuppressWarnings("unchecked")
    Expression<? extends Y> key =
        column.contains(".") ? (Path<Y>) pathOf(column) : root.get(column);
    pre.add(builder.between(key, lf, rt));
    return this;
  }

  public QueryWrapper<T> orderBy(String col, boolean asc) {
    Preconditions.checkArgument(col != null && col.length() > 0);
    Expression<?> key = col.contains(".") ? pathOf(col) : root.get(col);
    query.orderBy(asc ? builder.asc(key) : builder.desc(key));
    return this;
  }

  public QueryWrapper<T> orderBy(boolean asc, String... cols) {
    Preconditions.checkArgument(cols != null && cols.length >= 0);
    Order[] orders = new Order[cols.length];
    int idx = 0;
    Expression<?> key;
    for (String col : cols) {
      key = col.contains(".") ? pathOf(col) : root.get(col);
      orders[idx++] = asc ? builder.asc(key) : builder.desc(key);
    }
    query.orderBy(orders);
    return this;
  }

  public QueryWrapper<T> distinct() {
    query.distinct(true);
    return this;
  }

  public List<T> list() {
    return list(null, null);
  }

  public List<T> list(@Nullable Integer startPosition, @Nullable Integer maxResult) {
    Preconditions.checkArgument(
        startPosition == null && maxResult == null
            || startPosition != null && maxResult != null && startPosition >= 0 && maxResult > 0);

    Query<T> query = getQuery();
    log.debug(query.getQueryString());
    if (startPosition != null) {
      query.setFirstResult(startPosition);
    }
    if (maxResult != null) {
      query.setMaxResults(maxResult);
    }
    return query.list();
  }

  public long rowCount(boolean requireUiqueResult) {
    Query<Long> query = session.createQuery(where(counterQuery.select(builder.count(root))));
    log.debug(query.getQueryString());
    if (requireUiqueResult) {
      return query.uniqueResult();
    }
    return query.getSingleResult();
  }

  // When the records number is not big a alternative is
  // AbstractProducedQuery.uniqueElement(q.list())
  public T uniqueResult() {
    Query<T> query = getQuery();
    log.debug(query.getQueryString());
    return query.uniqueResult();
  }
}
