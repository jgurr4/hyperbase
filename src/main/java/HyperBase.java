import database.verticle.CacherVerticle;
import database.verticle.FileManagerVerticle;
import io.vertx.rxjava3.core.Vertx;

public class HyperBase {

    private final Vertx vertx;
    private final boolean debug = true;

    public static void main(final String[] args) {
        final HyperBase hyperBase = new HyperBase();
    }

    HyperBase() {
        vertx = Vertx.vertx();
        vertx.deployVerticle(new FileManagerVerticle());
        vertx.deployVerticle(new CacherVerticle());
    }

}
