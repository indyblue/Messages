/* global IntersectionObserver */
import { useState, useEffect } from 'preact/hooks';

const useFetch = urls => {
  const [data, setData] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [cache, setCache] = useState({});

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError(null);
      const promises = urls.map(async url => {
        if (cache[url]) {
          return cache[url];
        }
        const response = await fetch(url);
        if (!response.ok) throw new Error('Network response was not ok');
        const result = await response.json();
        setCache(x => ({ ...x, [url]: result }));
        return result;
      });

      try {
        const results = await Promise.all(promises);
        setData(results);
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [urls]);

  return { data, error, loading };
};

export default useFetch;

export const useIntersection = (callback, options = {}) => {
  const [ref, setRef] = useState(null);
  useEffect(() => {
    if (!ref) return;
    const observer = new IntersectionObserver(callback, options);
    observer.observe(ref);
    return () => {
      observer.disconnect();
    };
  }, [ref, callback, options]);
  return setRef;
};

export const useFetchPaginate = (url, options = {}) => {
  const [urls, setUrls] = useState([url]);
  const { data, error, loading } = useFetch(urls);
  const [hasMore, setHasMore] = useState(true);

  const loadMore = () => {
    if (loading || !hasMore) return;
    const lastPage = data[data.length - 1];
    if (lastPage && lastPage.nextPage && !urls.includes(lastPage.nextPage)) {
      setUrls(prev => [...prev, lastPage.nextPage]);
    } else {
      setHasMore(false);
    }
  };
  const intersectionCallback = entries => {
    if (entries[0].isIntersecting && hasMore) {
      loadMore();
    }
  };
  const observerRef = useIntersection(intersectionCallback, options.intersect);

  useEffect(() => {
    if (data.length === 0) return;
    const lastPage = data[data.length - 1];
    setHasMore(!!(lastPage && lastPage.nextPage && !urls.includes(lastPage.nextPage)));
  }, [data, urls]);

  return { data, error, loading, loadMore, hasMore, observerRef };
};

