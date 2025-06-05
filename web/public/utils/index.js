export const tryCatch = async fn => {
  try {
    const value = await fn();
    return [value, null];
  } catch (error) {
    return [null, error];
  }
};

export const tryFetch = async (url, opts) => {
  const { json, resp: includeResp, ...fetchOpts } = {
    json: true,
    resp: true,
    ...(opts || {}),
  };
  tryCatch(async () => {
    const resp = await fetch('/api/thread', fetchOpts);
    const value = json ? await resp.json() : await resp.text();
    if (includeResp) return { value, resp };
    else return value;
  });
};
