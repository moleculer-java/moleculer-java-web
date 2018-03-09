package services.moleculer.web.middleware.limiter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Set rate limit for an Action.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.TYPE })
public @interface RateLimit {

	int value();
	
	int window() default 1;
	
	TimeUnit unit() default TimeUnit.SECONDS;

}