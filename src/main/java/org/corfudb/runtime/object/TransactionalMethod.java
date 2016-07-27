package org.corfudb.runtime.object;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by mwei on 3/29/16.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TransactionalMethod {
    boolean readOnly() default false;

    String modifiedStreamsFunction() default "";
}
